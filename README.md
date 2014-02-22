# AIS

The Android Indexing Service is a service that allows other applications to search quickly search through files on a device using the Lucene search framework without having to implement Lucene themselves or deal with indexing. It also proviced centralized indexing to make it possible to search through all supported files on the device at once.
The service does not include the functionality to read files for indexing, relying on client applications to provide an interface for parsing files.
It is accessed through use of the Android Indexing Service Client Library which provides an interface for communicating with the service and provides template classes for allowing the service to use other applications for indexing files.
Version 0.1 of the indexing service only supports boolean queries and searching individual files.

#Planned changes in 0.2:
Multiple types of searches
Ability to search through multiple files or all files on the device

