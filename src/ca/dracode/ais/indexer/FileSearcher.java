/*******************************************************************************
 * Copyright 2014 Benjamin Winger.
 *
 * This file is part of Android Indexing Service.
 *
 * Android Indexing Service is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Android Indexing Service is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Android Indexing Service.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package ca.dracode.ais.indexer;

import android.util.Log;

import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.FieldCache.IntParser;
import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.search.FieldComparator.NumericComparator;
import org.apache.lucene.search.FieldComparatorSource;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ca.dracode.ais.indexdata.PageResult;

/*
 * FileSearcher.java
 * 
 * Contains functions for searching the lucene index
 * 
 * v0.3
 * Add capacity to check if the file needs to be updated by
 *  	comparing the index metadata's modified date with the file's modified
 *  	date
 *  Highlighter code needs to be tested
 */

public class FileSearcher {

	// NOTE - Standard searches take significantly longer than boolean ones due to their use of the highlighter
	//          which can make them take a long time to finish for common terms. They will be faster when the
	//          number of results is actually restricted but boolean searches should be recommended where speed is
    //          important
    public static final int QUERY_BOOLEAN = 0;
    public static final int QUERY_STANDARD = 1;


    private final String TAG = "ca.dracode.ais.androidindexer.FileSearcher";
    private IndexSearcher indexSearcher;
	private boolean interrupt = false;

    public FileSearcher() {
        IndexReader indexReader;
        IndexSearcher indexSearcher = null;
        try {
            File indexDirFile = new File(FileIndexer.getRootStorageDir());
            Directory tmpDir = FSDirectory.open(indexDirFile);
            indexReader = DirectoryReader.open(tmpDir);
            indexSearcher = new IndexSearcher(indexReader);
        } catch (IOException ioe) {
            Log.e(TAG, "Error", ioe);
        }

        this.indexSearcher = indexSearcher;
    }

    public boolean checkForIndex(String field, String value) throws IOException {
        //Log.i(TAG, "Checking for existance of " + value);
        BooleanQuery qry = new BooleanQuery();
        qry.add(new TermQuery(new Term(field, value)), BooleanClause.Occur.MUST);
        if (this.indexSearcher != null) {
            ScoreDoc[] hits;
            hits = indexSearcher.search(qry, 1).scoreDocs;
            return hits.length > 0;
        } else {
            Log.i(TAG, "Unable to check for index " + field + ":" + value);
            IndexReader indexReader;
            IndexSearcher indexSearcher = null;
            File indexDirFile = new File(FileIndexer.getRootStorageDir());
            Directory tmpDir = FSDirectory.open(indexDirFile);
            indexReader = DirectoryReader.open(tmpDir);
            indexSearcher = new IndexSearcher(indexReader);
            this.indexSearcher = indexSearcher;
            return false;
        }
    }

    public Document getDocument(String field, String value) throws IOException{
        //Log.i(TAG, "Checking for existance of " + value);
        BooleanQuery qry = new BooleanQuery();
        qry.add(new TermQuery(new Term(field, value)), BooleanClause.Occur.MUST);
        if (this.indexSearcher != null) {
            ScoreDoc[] hits;
            hits = indexSearcher.search(qry, 1).scoreDocs;
            if(hits!= null && hits.length > 0) {
                return indexSearcher.doc(hits[0].doc);
            }
        }
        return null;
    }

    public Document getMetaFile(String value) {
        BooleanQuery qry = new BooleanQuery();
        qry.add(new TermQuery(new Term("id", value + ":meta")),
                BooleanClause.Occur.MUST);
        ScoreDoc[] hits = null;
        try {
            hits = indexSearcher.search(qry, 1).scoreDocs;
        } catch (IOException e) {
            Log.e(TAG, "Error ", e);
        }
        assert hits != null;
        if (hits.length == 0) {
            return null;
        }
        Document doc = null;
        try {
            doc = indexSearcher.doc(hits[0].doc);
        } catch (IOException e) {
            Log.e(TAG, "Error ", e);
        }
        return doc;
    }

	public List<String> findName(String term, String field, List<String> constrainValues,
                                 String constrainField, int maxResults, int set, int type){
		Query qry = null;
		if(this.interrupt){
			this.interrupt = true;
			return null;
		}
		if (type == FileSearcher.QUERY_BOOLEAN) {
			String[] terms = term.split(" ");
			qry = new BooleanQuery();
			((BooleanQuery) qry).add(new WildcardQuery(new Term(field, "*" + term +"*")),
					BooleanClause.Occur.MUST);
		} else if (type == FileSearcher.QUERY_STANDARD) {
			try {
				qry = new QueryParser(Version.LUCENE_47, field,
						new SimpleAnalyzer(Version.LUCENE_47)).parse(term);
			} catch (ParseException e) {
				e.printStackTrace();
			}
			// qry.add(new Query(field, value));
		}
		if(this.interrupt){
			this.interrupt = true;
			return null;
		}
		if (qry != null) {
			BooleanQuery cqry = new BooleanQuery();
			for (String s : constrainValues) {
				cqry.add(new TermQuery(new Term(constrainField, s)),
						BooleanClause.Occur.MUST);
			}
			Filter filter = new QueryWrapperFilter(cqry);
			ScoreDoc[] hits = null;
			try {
				hits = indexSearcher.search(qry, filter, maxResults + maxResults*set).scoreDocs;
			} catch (IOException e) {
				Log.e(TAG, "Error ", e);
			}
			if(this.interrupt){
				this.interrupt = true;
				return null;
			}
			ArrayList<String> docs = new ArrayList<String>();
			for (int i = maxResults*set; i < hits.length && i < maxResults *set + maxResults; i++) {
				try {
					Document tmp =indexSearcher.doc(hits[i].doc);
					docs.add(tmp.get("path"));
				} catch (IOException e) {
					Log.e(TAG, "Error ", e);
				}
			}
			Log.i(TAG, "Found instance of term in " + docs.size() + " documents");
			if(this.interrupt){
				this.interrupt = true;
				return null;
			}
			return docs;
		}
		else {
			Log.e(TAG, "Query Type: " + type + " not recognised");
			return new ArrayList<String>();
		}
	}

	public PageResult[] findIn(String term, String field, List<String> constrainValues,
                               String constrainField, int maxResults, int set, int type){
		Query qry = null;
		if(this.interrupt){
			this.interrupt = true;
			return null;
		}
		if (type == FileSearcher.QUERY_BOOLEAN) {
			String[] terms = term.split(" ");
			qry = new BooleanQuery();
			((BooleanQuery) qry).add(new WildcardQuery(new Term(field, "*" + term +"*")),
					BooleanClause.Occur.MUST);
		} else if (type == FileSearcher.QUERY_STANDARD) {
			try {
				qry = new QueryParser(Version.LUCENE_47, field,
						new SimpleAnalyzer(Version.LUCENE_47)).parse(term);
			} catch (ParseException e) {
				e.printStackTrace();
			}
			// qry.add(new Query(field, value));
		}
		if(this.interrupt){
			this.interrupt = true;
			return null;
		}
		if (qry != null) {
			BooleanQuery cqry = new BooleanQuery();
			for (String s : constrainValues) {
				cqry.add(new TermQuery(new Term(constrainField, s)),
						BooleanClause.Occur.MUST);
			}
			Filter filter = new QueryWrapperFilter(cqry);
			ScoreDoc[] hits = null;
			try {
				hits = indexSearcher.search(qry, filter, maxResults*set + maxResults).scoreDocs;
			} catch (IOException e) {
				Log.e(TAG, "Error ", e);
			}
			if(this.interrupt){
				this.interrupt = true;
				return null;
			}
			ArrayList<Document> docs = new ArrayList<Document>();
			for (int i = maxResults*set; i < hits.length && i < maxResults*set + maxResults ; i++) {
				try {
					docs.add(indexSearcher.doc(hits[i].doc));
				} catch (IOException e) {
					Log.e(TAG, "Error ", e);
				}
			}
			if(this.interrupt){
				this.interrupt = true;
				return null;
			}
			Log.i(TAG, "Found instance of term in " + docs.size() + " documents");
			/**
			 * TODO - Add Highlighter Code to retrieve the generated phrase here (In progress)
			 **/
			try {
				int numResults = 0;
				PageResult[] results = new PageResult[docs.size()];
				for (int i = 0; i < docs.size() && numResults < maxResults; i++) {
					if(this.interrupt){
						this.interrupt = true;
						return null;
					}
					Document d = docs.get(i);
					int docPage = Integer.parseInt(d.get("page"));
					String name = d.get("path");
					if(type != FileSearcher.QUERY_BOOLEAN) {
						String contents = d.get("text");
						Highlighter highlighter = new Highlighter(new QueryScorer(qry));

						String[] frag = null;
						try {
							frag = highlighter.getBestFragments(new SimpleAnalyzer(Version.LUCENE_47), "text", contents, maxResults - numResults);
							numResults += frag.length;
						} catch (IOException e) {
							Log.e(TAG, "Error while reading index", e);
						} catch (InvalidTokenOffsetsException e) {
							Log.e(TAG, "Error while highlighting", e);
						}
						Log.i(TAG, "Frags: " + frag.length + " " + frag + " " + frag[0]);
						ArrayList<String> tmpList = new ArrayList<String>(Arrays
								.asList(frag != null ? frag : new String[0]));
						Log.i(TAG, "list " + tmpList.getClass().getName());
						results[i] = new PageResult(tmpList, docPage, name);
						Log.i(TAG, "" + results[i]);
					}
					else {
						ArrayList<String> tmp = new ArrayList<String>();
						tmp.add(term);
						results[i] = new PageResult(tmp, docPage, name);
					}

				}
				if(this.interrupt){
					this.interrupt = true;
					return null;
				}
				Log.i(TAG, "" +results.length);
				return results;
			}catch(Exception e){
				Log.e("TAG", "Error while Highlighting", e);
				return null;
			}
		} else {
			Log.e(TAG, "Query Type: " + type + " not recognised");
			return new PageResult[0];
		}
	}

    /** Adapted from Lucene 4.7 IntComparator
     *   Identical but intended for sorting pages of a document starting at a certain page.
     *   Previous pages are added to the end of the list
     *  */
    public abstract class PagedIntComparator extends NumericComparator<Integer> {
        protected final int[] values;
        private final IntParser parser;
        protected FieldCache.Ints currentReaderValues;
        protected int bottom;                           // Value of bottom of queue
        protected int topValue;

        PagedIntComparator(int numHits, String field, FieldCache.Parser parser,
                         Integer missingValue) {
            super(field, missingValue);
            values = new int[numHits];
            this.parser = (IntParser) parser;
        }

        @Override
        public void copy(int slot, int doc) {
            int v2 = currentReaderValues.get(doc);
            // Test for v2 == 0 to save Bits.get method call for
            // the common case (doc has value and value is non-zero):
            if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
                v2 = missingValue;
            }

            values[slot] = v2;
        }

        @Override
        public FieldComparator<Integer> setNextReader(AtomicReaderContext context) throws IOException {
            // NOTE: must do this before calling super otherwise
            // we compute the docsWithField Bits twice!
            currentReaderValues = FieldCache.DEFAULT.getInts(context.reader(), field, parser, missingValue != null);
            return super.setNextReader(context);
        }

        @Override
        public void setBottom(final int bottom) {
            this.bottom = values[bottom];
        }

        @Override
        public void setTopValue(Integer value) {
            topValue = value;
        }

        @Override
        public Integer value(int slot) {
            return Integer.valueOf(values[slot]);
        }
    }

    // TODO - Boolean Search returns a huge number of results for simple queries, maxResults must be implemented
    public PageResult[] find(String term, String field, String constrainValue,
                             String constrainField, int maxResults, int set, int type,
                             final int page) {
	    if(this.interrupt) {
		    this.interrupt = false;
		    return null;
	    }
        Query qry = null;
        if (type == FileSearcher.QUERY_BOOLEAN) {
	        String[] terms = term.split(" ");
            qry = new BooleanQuery();
            ((BooleanQuery) qry).add(new WildcardQuery(new Term(field, "*" + term +"*")),
                    BooleanClause.Occur.MUST);
        } else if (type == FileSearcher.QUERY_STANDARD) {
            try {
                qry = new QueryParser(Version.LUCENE_47, field,
                        new SimpleAnalyzer(Version.LUCENE_47)).parse(term);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            // qry.add(new Query(field, value));
        }
		    if(this.interrupt){
			    this.interrupt = false;
			    return null;
		    }
        if (qry != null) {
            BooleanQuery cqry = new BooleanQuery();
            cqry.add(new TermQuery(new Term(constrainField, constrainValue)),
                    BooleanClause.Occur.MUST);
            Filter filter = new QueryWrapperFilter(cqry);
            ScoreDoc[] hits = null;
            try {
                Log.i(TAG, "Searching...");
                hits = indexSearcher.search(qry, filter, maxResults * set + maxResults,
                        new Sort(new SortField("page",new FieldComparatorSource() {
                            @Override
                            public FieldComparator<?> newComparator(String fieldname, int numhits,
                                                                    int sortpos,
                                                                    boolean reversed) throws
                                    IOException {
                                return new PagedIntComparator(numhits, fieldname, null,
                                        null){
                                    public int compare(int slot1, int slot2){
                                        // TODO: there are sneaky non-branch ways to compute
                                        // -1/+1/0 sign
                                        // Cannot return values[slot1] - values[slot2] because that
                                        // may overflow
                                        final int v1 = this.values[slot1];
                                        final int v2 = this.values[slot2];
                                        if(v1 < page  && v2 >= page) {
                                            return 1;
                                        } else if (v1 >= page && v2 < page){
                                            return -1;
                                        } else{
                                            if(v1 > v2) {
                                                return 1;
                                            } else if(v1 < v2) {
                                                return -1;
                                            } else {
                                                return 0;
                                            }
                                        }
                                    }

                                    @Override
                                    public int compareBottom(int doc) {
                                        // TODO: there are sneaky non-branch ways to compute
                                        // -1/+1/0 sign
                                        // Cannot return bottom - values[slot2] because that
                                        // may overflow
                                        int v2 = this.currentReaderValues.get(doc);
                                        // Test for v2 == 0 to save Bits.get method call for
                                        // the common case (doc has value and value is non-zero):
                                        if(docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
                                            v2 = missingValue;
                                        }
                                        if(v2 < page){
                                            return -1;
                                        }
                                        if(this.bottom > v2) {
                                            return 1;
                                        } else if(this.bottom < v2) {
                                            return -1;
                                        } else {
                                            return 0;
                                        }
                                    }

                                    @Override
                                    public int compareTop(int doc){
                                        int docValue = this.currentReaderValues.get(doc);
                                        // Test for docValue == 0 to save Bits.get method call for
                                        // the common case (doc has value and value is non-zero):
                                        if(docsWithField != null && docValue == 0 && !docsWithField.get(doc)) {
                                            docValue = missingValue;
                                        }
                                        // Cannot use Integer.compare (it's java 7)
                                        if(this.topValue < page && docValue > page){
                                            return 1;
                                        }
                                        if(this.topValue < docValue) {
                                            return -1;
                                        } else if(this.topValue > docValue) {
                                            return 1;
                                        } else {
                                            return 0;
                                        }
                                    }
                                };
                            }
                        }))).scoreDocs;
            } catch (IOException e) {
                Log.e(TAG, "Error ", e);
            }
	        if(this.interrupt){
		        this.interrupt = true;
		        return null;
	        }

            Log.i(TAG, "Sorted " + hits.length);
            ArrayList<Document> docs = new ArrayList<Document>();
            for (int i = maxResults*set; i < hits.length || i < (maxResults * set + maxResults);
                 i++) {
                try {
	                Document tmp = indexSearcher.doc(hits[i].doc);
	                int docPage = Integer.parseInt(tmp.get("page"));
	                docs.add(tmp);
	            } catch (IOException e) {
                    Log.e(TAG, "Error ", e);
                }
            }
	        if(this.interrupt){
		        this.interrupt = false;
		        return null;
	        }
	        Log.i(TAG, "Found instance of term in " + hits.length + " documents");
            /**
             * TODO - Add Highlighter Code to retrieve the generated phrase here (In progress)
             **/
            try {
	            int numResults = 0;
	            ArrayList<PageResult> results = new ArrayList<PageResult>();
	            for (int i = 0; i < docs.size(); i++) {
		            if(this.interrupt){
			            this.interrupt = false;
			            return null;
		            }
		            Document d = docs.get(i);
		            int docPage = Integer.parseInt(d.get("page"));
		            if(type != FileSearcher.QUERY_BOOLEAN) {
			            String contents = d.get("text");
			            Highlighter highlighter = new Highlighter(new QueryScorer(qry));

			            String[] frag = null;
			            try {
				            frag = highlighter.getBestFragments(new SimpleAnalyzer(Version.LUCENE_47), "text", contents, maxResults - numResults);
				            numResults += frag.length;
			            } catch (IOException e) {
				            Log.e(TAG, "Error while reading index", e);
			            } catch (InvalidTokenOffsetsException e) {
				            Log.e(TAG, "Error while highlighting", e);
			            }
			            Log.i(TAG, "Frags: " + frag.length + " " + frag + " " + frag[0]);
			            ArrayList<String> tmpList = new ArrayList<String>(Arrays
					            .asList(frag != null ? frag : new String[0]));
			            Log.i(TAG, "list " + tmpList.getClass().getName());
			            results.add(new PageResult(tmpList, docPage, constrainValue));
			            Log.i(TAG, "" + results.get(i));
		            }
					else {
                        ArrayList<String> tmp = new ArrayList<String>();
			            tmp.add(term);
			            results.add(new PageResult(tmp, docPage, constrainValue));
		            }

	            }
	            Log.i(TAG, "" +results.size());
	            if(this.interrupt){
		            this.interrupt = false;
		            return null;
	            }
	            return results.toArray(new PageResult[0]);
            }catch(Exception e){
		        Log.e("TAG", "Error while Highlighting", e);
	            return null;
	        }
        } else {
            Log.e(TAG, "Query Type: " + type + " not recognised");
            return new PageResult[0];
        }
    }

	public boolean interrupt(){
		boolean tmp = this.interrupt;
		this.interrupt = true;
		return tmp;
	}
}