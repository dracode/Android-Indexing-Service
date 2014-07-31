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

package ca.dracode.ais.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import ca.dracode.ais.R;
import ca.dracode.ais.indexclient.MClientService;
import ca.dracode.ais.indexer.FileIndexer;

/*
 * IndexService.java
 * 
 * Service that crawls through the host device's file system and tries to index 
 * indexable files. It uses info files generated by the client library to access remote services
 *
 *  TODO - Instead of connecting to index services to index each file found, make a list of which files
 *      need to be indexed by each service and connect to each service only once
 *       - Create a progress indicator by using the total size of the documents found while crawling
 *       as each document is indexed, update the main notification with the filesizeindex/totalfilesize
 *      noting the amount as a percentage
 */

public class IndexService extends Service {
	private static String TAG = "ca.dracode.ais.service.IndexService";
	private final IBinder mBinder = new LocalBinder();
	protected boolean interrupt;
	private NotificationManager nm;
	private int mIsBound = 0;
	private boolean doneCrawling;
	private ArrayList<ParserService> services;
	private Queue<Indexable> pIndexes;
	private FileIndexer indexer;
    private final int maxTasks = 30;
    private int tasks = 0;

	@Override
	public void onCreate() {
		this.services = new ArrayList<ParserService>();
		nm = (NotificationManager) this
				.getSystemService(NOTIFICATION_SERVICE);
		IndexService.this.notifyPersistent(
				getText(R.string.notification_indexer_started), 1);
			boolean mExternalStorageAvailable;
			boolean mExternalStorageWriteable;
			String state = Environment.getExternalStorageState();

			if (Environment.MEDIA_MOUNTED.equals(state)) {
				// We can read and write the media
				mExternalStorageAvailable = mExternalStorageWriteable = true;
			} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
				// We can only read the media
				mExternalStorageAvailable = true;
				mExternalStorageWriteable = false;
			} else {
				// Something else is wrong. It may be one of many other states,
				// but
				// all we need
				// to know is we can neither read nor write
				mExternalStorageAvailable = mExternalStorageWriteable = false;
			}

			if (mExternalStorageAvailable && mExternalStorageWriteable) {
				this.loadServices(new File(Environment
						.getExternalStorageDirectory() + "/Android/data"));
			} else {
				notify("Error: External Storage not mounted", 2);
				return;
			}
			this.loadServices(Environment.getExternalStorageDirectory());
			this.indexer = new FileIndexer();
			this.pIndexes = new LinkedList<Indexable>();
			this.doneCrawling = false;
			new Thread(new Runnable() {
				public void run() {
                    //Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
					while (true) {
						try {
							Thread.sleep(0, 10);
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}
						Indexable tmp = pIndexes.poll();
						if (tmp != null) {
                            Log.i(TAG, "Indexing: " + tmp.file.getAbsolutePath());
							if (tmp.tmpData == null || tmp.tmpData.size() == 0) {
								indexer.buildIndex(tmp.file.getAbsolutePath());
							} else {
								try {
									indexer.buildIndex(tmp.tmpData,
											tmp.file);
                                    tasks--;
								} catch (Exception e) {
									Log.e(TAG, "Error ", e);
								}
							}
						}
						if (doneCrawling && mIsBound == 0
								&& pIndexes.size() == 0) {
							Log.i(TAG, "Done Indexing, Closing... ");
							indexer.close();
							doneCrawling = false;
							IndexService.this.stopSelf();
						}
					}
				}
			}).start();
            new Thread(new Runnable(){
                public void run(){
                    //Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    try {
                        crawl(Environment.getExternalStorageDirectory());
                        doneCrawling = true;
                    } catch(IOException e){
                        Log.e(TAG, "Error", e);
                    }
                }
            }).start();
	}

	public void loadServices(File directory) {
		File[] contents = directory.listFiles();
		for (File content : contents) {
			if (content.canRead()) {
				if (content.isFile()) {
					if (content.getName().toLowerCase().endsWith(".is")) {
						BufferedReader br = null;
						try {
							br = new BufferedReader(new FileReader(
									content.getAbsolutePath()));
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						}
						if (br != null) {
							try {
								String name = br.readLine();
								Log.i(TAG, "Found service of name: " + name);
								ArrayList<String> tmpExt = new ArrayList<String>();
								if (name != null) {
									String tmp;
									while ((tmp = br.readLine()) != null) {
										tmpExt.add(tmp);
										Log.i(TAG, "Found Extension: " + tmp);
									}
								}
								br.close();
								this.services.add(new ParserService(name,
										tmpExt));
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				} else {
					this.loadServices(content);
				}
			}
		}
	}

	public void onDestroy() {
		nm.cancel(1);
	}

	public void crawl(File directory) throws IOException {
		// Log.i(TAG, "Indexing directory " + directory.getAbsolutePath());
		File[] contents = directory.listFiles();
        Log.i(TAG, "Indexer Entered Directory " + directory.getAbsolutePath());
		if (contents != null) {
			for (File content : contents) {
                while(tasks > maxTasks){
                    try {
                        Thread.sleep(10);
                    } catch(InterruptedException e){
                        Log.e(TAG, "Error", e);
                    }
                }
				if (content.canRead()) {
					if (content.isFile()) {
						String serviceName = null;
						int size = services.size();
						for (int j = 0; j < size; j++) {
							int mLoc = content.getName().lastIndexOf(".") + 1;
							if (mLoc != 0) {
								boolean found = services.get(j).checkExtension(
										content.getName().substring(mLoc)
												.toLowerCase()
								);
								if (found) {
									serviceName = services.get(j).getName();
								}
							}
						}
						if (serviceName != null) {
							String files = "";
							try {
								BufferedReader br = new BufferedReader(
										new FileReader(
												FileIndexer.getRootStorageDir()
														+ "/FileLocations.txt"
										)
								);
								String tmp;
								while ((tmp = br.readLine()) != null) {
									files = files.concat(tmp);
									files = files.concat("\n");
								}
								br.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
							if (!files.contains(content.getAbsolutePath())) {
								try {
									BufferedWriter bw = new BufferedWriter(
											new FileWriter(
													FileIndexer.getRootStorageDir()
															+ "/FileLocations.txt"
											)
									);
									bw.append(files).append(content.getAbsolutePath()).append("\n");
									bw.close();
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
							createIndex(content, serviceName);
						} else {
							createIndex(content, null);
						}
					} else if(content.isDirectory()){
						createIndex(content, null);
					}
				}
			}
			for (File content : contents) {
				if (content.canRead()) {
					if (content.isDirectory()
							&& content.getAbsolutePath().equals(
							content.getCanonicalPath())) {
						this.crawl(content);
					}
				}
			}
		}
	}

	public void createIndex(File content, String serviceName){
		try {
            int state =indexer.checkForIndex("id",
                    content.getAbsolutePath() + ":meta", content.lastModified());
			if (state == 0) {
				Log.i(TAG, "Found index for " + content.getName() + "; skipping.");
			} else if(state == 1) {
                Log.i(TAG, "Index for " + content.getName() + " out of date, building index");
                try {
                    new RemoteBuilder(
                            content,
                            serviceName);
                    tasks++;
                } catch (Exception e) {
                    Log.e(TAG, "" + e.getMessage());
                }
            }
            else if(state == -1){
				Log.i(TAG, "Index for " + content.getName() + " not found, building index");
				try {
					new RemoteBuilder(
							content,
							serviceName);
                    tasks++;
				} catch (Exception e) {
					Log.e(TAG, "" + e.getMessage());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void notify(CharSequence c, int id) {
		Notification notification = new Notification(R.drawable.file_icon, c,
				System.currentTimeMillis());
		Intent intent = new Intent(getApplicationContext(), IndexService.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(
				getApplicationContext(), 0, intent, 0);
		notification.setLatestEventInfo(this, c, c, pendingIntent);
		nm.notify(id, notification);
	}

	public void notifyPersistent(CharSequence c, int id) {
		Notification not = new Notification(R.drawable.file_icon, c,
				System.currentTimeMillis());
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, IndexService.class),
				Notification.FLAG_ONGOING_EVENT);
		not.flags = Notification.FLAG_ONGOING_EVENT;
		not.setLatestEventInfo(this, "AIS", "Indexing...", contentIntent);
		nm.notify(1, not);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	private class Indexable {
		private ArrayList<String> tmpData;
		private File file;
		private IBinder mService;
		private String serviceName = null;
		private RemoteBuilder builder;

		public Indexable(ArrayList<String> tmpData, File file,
		                 IBinder mService, String serviceName, RemoteBuilder builder) {
			super();
			this.tmpData = tmpData;
			this.file = file;
			this.mService = mService;
			this.serviceName = serviceName;
			this.builder = builder;
		}

	}

	private class RemoteBuilder {
		private ArrayList<String> tmpData;
		private File file;
		private IBinder mService;
		private String serviceName = null;

		public RemoteBuilder(File file, String serviceName) {
			this.file = file;
			this.serviceName = serviceName;
			if(serviceName != null) {
				this.doBindService(getApplicationContext());
			} else{
				pIndexes.add(new Indexable(null, file, null,
						null, RemoteBuilder.this));
			}
		}

		void doBindService(Context c) {
			// Establish a connection with the service. We use an explicit
			// class name because we want a specific service implementation that
			// we know will be running in our own process (and thus won't be
			// supporting component replacement by other applications).
			Log.i(TAG, "Binding to service...");

			if (serviceName == null) {
				return;
			}
            /*
             * while(mIsBound > 0){ try { Thread.sleep(1); } catch
			 * (InterruptedException e) { e.printStackTrace(); } }
			 */
			if (c.bindService(new Intent(serviceName), mConnection,
					Context.BIND_AUTO_CREATE)) {
				mIsBound++;
			}
			Log.i(TAG, "Service is bound = " + mIsBound);
		}

		public void doUnbindService(Context c) {
			if (mIsBound > 0) {
				// Detach our existing connection.
				c.unbindService(mConnection);
				mIsBound--;
                /*
				 * if (mIsBound == 0) { nm.cancel(1); stopSelf(); }
				 */
			}
		}

		private ServiceConnection mConnection = new ServiceConnection() {
			// Called when the connection with the service is established
			public void onServiceConnected(ComponentName className,
			                               IBinder service) {
				// Following the example above for an AIDL interface,
				// this gets an instance of the IRemoteInterface, which we can
				// use
				// to call on the service
				mService = service;
				Log.i(TAG, "Service: " + mService);
				try {
					MClientService tmp = MClientService.Stub
							.asInterface(mService);
					tmp.loadFile(file.getAbsolutePath());
					tmpData = new ArrayList<String>();
					int pages = tmp.getPageCount();
					for (int i = 0; i < pages; i++) {
						tmpData.add(tmp.getWordsForPage(i));
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				// build();
				pIndexes.add(new Indexable(tmpData, file, mService,
						serviceName, RemoteBuilder.this));
				doUnbindService(getApplicationContext());
			}

			// Called when the connection with the service disconnects
			// unexpectedly
			public void onServiceDisconnected(ComponentName className) {
				Log.e(TAG, "Service has unexpectedly disconnected");
				mService = null;
				mIsBound--;
			}
		};
	}

	public class LocalBinder extends Binder {
		IndexService getService() {
			return IndexService.this;
		}
	}

}
