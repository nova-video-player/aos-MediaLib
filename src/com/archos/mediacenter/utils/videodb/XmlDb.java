// Copyright 2017 Archos SA
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.utils.videodb;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.ftp.AuthenticationException;
import com.archos.mediacenter.filecoreextension.upnp2.FileEditorFactoryWithUpnp;
import com.archos.mediacenter.filecoreextension.upnp2.RawListerFactoryWithUpnp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jcifs2.smb.SmbException;

public class XmlDb  implements Callback {
    private final static String TAG = "XmlDb";

    private static XmlDb sXmlDb;

    private static final boolean DBG = false;

    private static final int MSG_PARSE_TIMEOUT = 0;
    private static final int MSG_PARSE_OK = 1;
    private static final int PARSING_TIMEOUT = 7; // Time in seconds
    public static final String FILE_EXTENSION = "xml";
    public static final String FILE_NAME = ".archos.resume."+FILE_EXTENSION;
    private final Handler mUiThreadHandler = new Handler(Looper.getMainLooper(), this);
    private static final Map<String, WriteTask> sRemoteWriteTasks = new HashMap<String, WriteTask>();
    private static final Map<String, ParseTask> sRemoteParseTasks = new HashMap<String, ParseTask>();
    private static final Map<Uri, VideoDbInfo> sRemoteCache = new HashMap<>();
    private final ArrayList<ResumeChangeListener> mResumeChangeListener;
    private List<ParseListener> mOnParseListeners;

    public static VideoDbInfo getEntry(Uri videoFileLocation) {
        return sRemoteCache.get(videoFileLocation);
    }



    public static class ParseResult {
        public final boolean success;
        private final Uri location;

        public ParseResult(Uri location, boolean success) {
            this.location = location;
            this.success = success;
        }


    }



    private static class ContentHandler extends DefaultHandler {
        private final Uri mLocation;
        public VideoDbInfo mCurrentEntry = null;
        private final StringBuffer mBuffer = new StringBuffer();
        private VideoDbInfo mResult = new VideoDbInfo();

        public ContentHandler(Uri location) {
            mLocation = location;
        }
        private int getInt() {
            try {
                return Integer.parseInt(mBuffer.toString().trim());
            } catch (NumberFormatException e) {}
            return 0;
        }

        private String getString() {
            return mBuffer.toString().trim();
        }

        public void startElement(String nsUri, String localName, String qName,
                Attributes attrs) {
            mBuffer.setLength(0);
            if (localName.equals("network_database"))
                mCurrentEntry = new VideoDbInfo();
        }

        public void characters (char ch[], int start, int length) {
            mBuffer.append(ch, start, length);
        }

        public void endElement(String nsUri, String localName, String qName) {
            if (mCurrentEntry == null)
                return;
            if (localName.equals("path")) {
                String path = getString();
                mCurrentEntry.setFile(getFilePath(mLocation, path));
            }
            else if (localName.equals("last_position"))
                mCurrentEntry.resume = getInt();
            else if (localName.equals("bookmark_position"))
                mCurrentEntry.bookmark = getInt();
            else if (localName.equals("audio_track"))
                mCurrentEntry.audioTrack = getInt();
            else if (localName.equals("subtitle_track"))
                mCurrentEntry.subtitleTrack = getInt();
            else if (localName.equals("subtitle_delay"))
                mCurrentEntry.subtitleDelay = getInt();
            else if (localName.equals("subtitle_ratio"))
                mCurrentEntry.subtitleRatio = getInt();
            else if (localName.equals("last_time_played"))
                mCurrentEntry.lastTimePlayed = Long.decode(getString());
            else if (localName.equals("network_database")) {
                mResult = mCurrentEntry;
                sRemoteCache.put(mCurrentEntry.uri, mCurrentEntry);
                mCurrentEntry = null;
            }
        }
        public VideoDbInfo getResult() {
            return mResult;
        }
    }

    private static class ParseTask extends AsyncTask<Void, Integer, VideoDbInfo> {
        private final Uri mLocation;
        private Listener mListener;

        private interface Listener {
            void onResult(VideoDbInfo result);
        }
        public ParseTask(Uri location) {
            mLocation = location;
        }

        public void setListener(Listener listener) {
            mListener = listener;
        }

        public void abort() {
            cancel(true);
            mListener = null;
        }

        @Override
        protected VideoDbInfo doInBackground(Void... params) {
            return parseXml(mLocation);
        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected void onPostExecute(VideoDbInfo result) {

            if (mListener != null)
                mListener.onResult(result);
        }
    }
    public interface ResumeChangeListener{
        /**
         * each time a new resume is written
         * @param videoFile
         * @param resumePercent
         */
        void onResumeChange(Uri videoFile, int resumePercent);
    }

    public interface ParseListener{

        void onParseFail(ParseResult parseResult);

        void onParseOk(ParseResult obj);
    }

    private class WriteTask extends AsyncTask<Void, Integer, Void> {
        private final VideoDbInfo mVideoDbInfo;
        /**
         *
         *
         * @param videoDbInfo
         */
        public WriteTask(VideoDbInfo videoDbInfo) {
            mVideoDbInfo = videoDbInfo;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (DBG) Log.d(TAG, "doInBackground: " + mVideoDbInfo.uri);
            boolean ret = writeXml(mVideoDbInfo);
            if (DBG) Log.d(TAG, "writeXml: " + ret);
            return null;
        }

        @Override
        protected void onPreExecute() {
            sRemoteWriteTasks.put(mVideoDbInfo.uri.toString(), this);
        }

        @Override
        protected void onPostExecute(Void result) {
            sRemoteWriteTasks.remove(mVideoDbInfo.uri.toString());
            sRemoteCache.put(mVideoDbInfo.uri, mVideoDbInfo);
            notifyResumeChange(mVideoDbInfo.uri, (int) ((float) mVideoDbInfo.resume / (float) mVideoDbInfo.duration * 100.0));
        }
    }
    public synchronized void addResumeChangeListener(ResumeChangeListener listener){
        mResumeChangeListener.add(listener);
    }
    public synchronized void removeResumeChangeListener(ResumeChangeListener listener){
        mResumeChangeListener.remove(listener);
    }
    private synchronized void notifyResumeChange(Uri videoFile, int newResumePercent){
        for(ResumeChangeListener listener : mResumeChangeListener)
            listener.onResumeChange(videoFile,newResumePercent);
    }

    /**
     * look for a DB file associated with video and parse it
     * @param videoFileUri
     * @return
     */
    private static VideoDbInfo parseXml(Uri videoFileUri) {
        final Uri location = getReadXmlPath(videoFileUri);
        if(location==null)
            return null;
        InputStream fis=null;
        try {

                try {
                    fis = FileEditorFactoryWithUpnp.getFileEditorForUrl(location, null).getInputStream();
                } catch (MalformedURLException e1) {
                    Log.e(TAG, "parseXml: Error: " + e1);
                    return null;
                } catch (IOException e2) {
                    Log.e(TAG, "parseXml: Error: " + e2);
                    return null;
                } catch (Exception e) {
                    Log.e(TAG, "parseXml: Error: " + e);
                    return null;

                }

            if (fis == null) {
                Log.d(TAG, "parseXml: Invalid InputStream");
                return null;
            }
            ContentHandler handler = new ContentHandler(location);
            XMLReader parser = XMLReaderFactory.createXMLReader();
            parser.setContentHandler(handler);
            parser.parse(new InputSource(fis));
            fis.close();
            return handler.getResult();
        } catch (InterruptedIOException e) {
            Log.e(TAG, "parseXml: timeout while parsing files.", e);
        } catch (FileNotFoundException e) {
            // when file was never created.
        } catch (SmbException e) {
            // when file was never created.
        } catch (SAXException e) {
            Log.e(TAG, "parseXml: Error while parsing files.", e);
        } catch (IOException e) {
            Log.e(TAG, "parseXml: Error while reading files.", e);
            // delete file since it may be corrupt
        }
        finally {
            if(fis!=null)
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }

        return null;
    }

    /**
     * return list of DB of shape videofilename.extension.resumepoint.archos.xml
     * @param videoFile
     * @return
     */
    public static List<MetaFile2> getListOfDBForUri(Uri videoFile){
        Uri toList = FileUtils.getParentUrl(videoFile);
        if(toList!=null){
           RawLister rl = RawListerFactoryWithUpnp.getRawListerForUrl(toList);
            try {

                List<MetaFile2> list = rl.getFileList();
                if(list!=null)
                return extractAssociatedWithUriDbXmlMetafileFromList(list, videoFile);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (AuthenticationException e) {
                e.printStackTrace();
            } catch (SftpException e) {
                e.printStackTrace();
            } catch (JSchException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * from a list of metafiles, return metafiles that are DB of a specific videofile (in the shape "nameofvideofile.extensionofvideo.resumepoint.archosresume.xml")
     * @param metaFile2List
     * @param videoFile
     * @return
     */
    public static List<MetaFile2> extractAssociatedWithUriDbXmlMetafileFromList(List<MetaFile2> metaFile2List, Uri videoFile){
        List<MetaFile2> toReturn = new ArrayList<>();
        String name = FileUtils.getName(videoFile);
            for(MetaFile2 mf : metaFile2List){
                if(mf.getName().matches("^\\."+ Pattern.quote(name)+"\\.[0-9]*\\"+FILE_NAME)){
                    toReturn.add(mf);
                }
            }
        return toReturn;

    }

    /**
     * be aware : percentage
     * @param metaFile2List
     * @param videoFile
     * @return
     */
    public static int extractResumePointForSpecificVideoFileFromListOfFiles(List<MetaFile2> metaFile2List, Uri videoFile){
        List<MetaFile2> dbList = extractAssociatedWithUriDbXmlMetafileFromList(metaFile2List, videoFile);
        if(dbList!=null&&!dbList.isEmpty()){
            Uri uri = dbList.get(0).getUri();

            VideoDbInfo info =  extractBasicVideoInfoFromXmlFileName(uri);
            if(info!=null)
                return info.resume;

        }
        return -1;
    }

    /**
     * extract percentage resume  and video name from database filename
     * name.extension.resume.archosresume.xml -> {resume , name.extension}
     * if duration < 0 -> resume is a percent
     * @param fileUri
     * @return VideoDbInfo
     */
    public static VideoDbInfo extractBasicVideoInfoFromXmlFileName(Uri fileUri){
        String pattern = ".*\\.(\\d+)\\"+FILE_NAME;
        Pattern r = Pattern.compile(pattern);
        String filename = FileUtils.getName(fileUri);
        Matcher m = r.matcher(filename);
        if (m.find()) {

            int resume = Integer.parseInt(m.group(1));
            String videoFile = extractVideoFileNameFromNFOFileName(filename);
            Uri parentUri = FileUtils.getParentUrl(fileUri);
            Uri videoFileUri = Uri.withAppendedPath(parentUri, videoFile);
            VideoDbInfo info = null;
            if((info=sRemoteCache.get(videoFileUri))!=null){ //update resume
                if(info.duration>0)
                    info.resume= (int) ((float) resume * (float) info.duration / 100.0);
                else
                    info.resume = resume;
            }
            else{
                info = new VideoDbInfo(videoFileUri);
                info.duration=-1;
                info.resume=resume;
                sRemoteCache.put(videoFileUri,info);
            }
            return info;
        }
        return null;
    }

    /**
     * extract videoname from filename
     *
     * @param xmlFilename
     * @return
     */
    public static String extractVideoFileNameFromNFOFileName(String xmlFilename){
        String pattern = "\\.(.*)\\.\\d+\\"+FILE_NAME;
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(xmlFilename);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
    public static Uri getReadXmlPath(Uri videoFile){
        List<MetaFile2> list = getListOfDBForUri(videoFile);
        if(list!=null&&!list.isEmpty())
            return list.get(0).getUri();
        return null;
    }

    public static void deleteAssociatedResumeDatabase(Uri videoFile) {
        List <MetaFile2> todelete = getListOfDBForUri(videoFile);
        if(todelete!=null&&!todelete.isEmpty()){
            for(MetaFile2 mf2 : todelete){
                try {
                    mf2.getFileEditorInstance(null).delete();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * Write content of a VideoDbInfo to a specific path
     *
     * @param entry
     * @return
     */
    private static boolean writeXml(VideoDbInfo entry) {
        //first, merge remote cache with cache in memory
        if(entry==null)
            return false;
        //delete old db files

        deleteAssociatedResumeDatabase(entry.uri);
        final StringWriter writer = new StringWriter(5000);
        writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.append("<!-- Archos MediaCenter metadata -->\n");
        writer.append("<network_database>\n");
        writeXmlEntry(writer, entry);
        writer.append("</network_database>\n");
        String xmlContent = writer.toString();

        OutputStream os = null;

            try {
                //TODO first, we look for an old db, then we delete it
                Uri xmlUri = getXmlPath(entry);
                if(xmlUri==null)
                    return false;
                FileEditor fileEditor = FileEditorFactoryWithUpnp.getFileEditorForUrl(xmlUri, null);
                if (DBG) Log.d(TAG, "xmlPath: "+ xmlUri);
                // Delete existing file to avoid overwrite issue (end of previous content still there is the new content is shorter)
                if (fileEditor.exists()) {
                    fileEditor.delete();
                }
                os = fileEditor.getOutputStream();
            } catch (Exception e1) {
                Log.e(TAG, "writeXml: Error: " + e1);
                return false;
            }


        if (os != null) {
            try {
                os.write(xmlContent.getBytes());
            } catch (IOException e) {
                Log.e(TAG, "writeXml: Error: " + e);
                return false;
            }
            finally {
                if(os!=null)
                    try {
                        os.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
        }


        return true;
    }


    private XmlDb(){
        System.setProperty("org.xml.sax.driver", "org.xmlpull.v1.sax2.Driver");
        mResumeChangeListener = new ArrayList<>();
        mOnParseListeners = Collections.synchronizedList(new ArrayList<ParseListener>());
    }


    private static Uri getFilePath(Uri xmlLocation, String videoPath) {
        if (videoPath == null)
            return null;
        Uri parentUri = FileUtils.getParentUrl(xmlLocation);
        if(parentUri!=null && parentUri.getPath()!=null && !parentUri.getPath().isEmpty()){
            Uri xml = Uri.withAppendedPath(parentUri, videoPath);
            return xml;
        }
        return null;
    }
    private static Uri getXmlPath(VideoDbInfo videoFile) {
        if (videoFile == null)
            return null;
        Uri parentUri = FileUtils.getParentUrl(videoFile.uri);
        if(parentUri!=null && parentUri.getPath()!=null && !parentUri.getPath().isEmpty()){
            //calculating percent
            double percent = (float) videoFile.resume/(float)videoFile.duration * 100.0;
            Uri xml = Uri.withAppendedPath(parentUri, "."+FileUtils.getName(videoFile.uri)+"."+((int)percent)+FILE_NAME);
            return xml;
        }
        return null;
    }


    public static synchronized XmlDb getInstance() {
        if (sXmlDb == null) {
            sXmlDb = new XmlDb();
        }
        return sXmlDb;
    }

    private static void writeXmlEntryElement(StringWriter writer, String name, String element) {
        writer.append("<").append(name).append(">").append(element).append("</").append(name).append(">\n");
    }

    private static void writeXmlEntry(StringWriter writer, VideoDbInfo entry) {
        writer.append("<path>");
        escapeAndAppendString(FileUtils.getName(entry.uri), writer);
        writer.append("</path>\n");
        if (entry.resume == -2 || entry.resume >= 0)
            writeXmlEntryElement(writer, "last_position", Integer.toString(entry.resume));
        if (entry.bookmark >= 0)
            writeXmlEntryElement(writer, "bookmark_position", Integer.toString(entry.bookmark));
        if (entry.audioTrack >= 0)
            writeXmlEntryElement(writer, "audio_track", Integer.toString(entry.audioTrack));
        if (entry.subtitleTrack >= 0)
            writeXmlEntryElement(writer, "subtitle_track", Integer.toString(entry.subtitleTrack));
        if (entry.subtitleDelay != 0)
            writeXmlEntryElement(writer, "subtitle_delay", Integer.toString(entry.subtitleDelay));
        if (entry.subtitleRatio != 0)
            writeXmlEntryElement(writer, "subtitle_ratio", Integer.toString(entry.subtitleRatio));
                if (entry.lastTimePlayed >= 0)
            writeXmlEntryElement(writer, "last_time_played", Long.toString(entry.lastTimePlayed));
    }
    public void addParseListener(ParseListener pl){
        synchronized (mOnParseListeners) {
            if (!mOnParseListeners.contains(pl))
                mOnParseListeners.add(pl);
        }
    }
    public void removeParseListener(ParseListener parseListener) {
        synchronized (mOnParseListeners) {
            mOnParseListeners.remove(parseListener);
        }
    }


    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_PARSE_OK:
                if (DBG) Log.d(TAG, "MSG_PARSE_OK");
                synchronized (mOnParseListeners) {
                    // some listeners want to remove themselves for list after being called. to avoid concurrent exception, to not iterate on main list
                    List<ParseListener> tmp = new ArrayList(mOnParseListeners);
                    for (ParseListener pl : tmp) {
                        pl.onParseOk((ParseResult) msg.obj);
                    }
                }
                return true;
            case MSG_PARSE_TIMEOUT: {
                if (DBG) Log.d(TAG, "MSG_PARSE_TIMEOUT");
                Uri location = (Uri) msg.obj;
                ParseTask task = sRemoteParseTasks.get(location.toString());
                if (task != null) {
                    task.abort();
                    sRemoteParseTasks.remove(location.toString());
                    synchronized (mOnParseListeners) {
                        // some listeners want to remove themselves for list after being called. to avoid concurrent exception, to not iterate on main list
                        List<ParseListener> tmp = new ArrayList(mOnParseListeners);
                        for (ParseListener pl : tmp) {
                            pl.onParseFail(new ParseResult(location, false));
                        }
                    }
                }
                return true;

            }
        }
        return false;
    }

    private void notifyChanged(Uri location, boolean success) {
        mUiThreadHandler.obtainMessage(MSG_PARSE_OK,
                        new ParseResult(location, success)).sendToTarget();
    }



    public void parseXmlLocation(final Uri videoFileUri) {
        if (DBG) Log.d(TAG, "parseCommon:" + videoFileUri);


            ParseTask task;
                if (sRemoteWriteTasks.get(videoFileUri.toString()) != null) {
                    if (DBG) Log.d(TAG, "writting task is running: assume we are up to date");
                    notifyChanged(videoFileUri, true);
                    return;
                }
                if (sRemoteParseTasks.get(videoFileUri.toString()) != null) {
                    if (DBG) Log.d(TAG, "parsing task is already running:");
                    return;
                }

            task = new ParseTask(videoFileUri);
            final ParseTask.Listener listener = new ParseTask.Listener() {
                @Override
                public void onResult(VideoDbInfo result) {
                    mUiThreadHandler.removeMessages(MSG_PARSE_TIMEOUT);
                    Log.d(TAG, "onResult ");

                    notifyChanged(videoFileUri, result != null);
                }
            };
            task.setListener(listener);
            task.execute();
            mUiThreadHandler.sendMessageDelayed(
                    mUiThreadHandler.obtainMessage(MSG_PARSE_TIMEOUT, videoFileUri),
                    PARSING_TIMEOUT * 1000);

    }



    public void writeXmlRemote(VideoDbInfo videoDbInfo) {
        WriteTask task;


        task = sRemoteWriteTasks.get(videoDbInfo.uri.toString());
        if (task != null) {
            task.cancel(true);
        }

        task = new WriteTask(videoDbInfo);
        task.execute();
    }






    // taken from FastXmlSerializer, slightly modified
    private static final String ESCAPE_TABLE[] = new String[] {
        null,     null,     null,     null,     null,     null,     null,     null,  // 0-7
        null,     null,     null,     null,     null,     null,     null,     null,  // 8-15
        null,     null,     null,     null,     null,     null,     null,     null,  // 16-23
        null,     null,     null,     null,     null,     null,     null,     null,  // 24-31
        null,     null,     "&quot;", null,     null,     null,     "&amp;",  null,  // 32-39
        null,     null,     null,     null,     null,     null,     null,     null,  // 40-47
        null,     null,     null,     null,     null,     null,     null,     null,  // 48-55
        null,     null,     null,     null,     "&lt;",   null,     "&gt;",   null,  // 56-63
    };
    private static void escapeAndAppendString(final String string, final StringWriter writer) {
        final int N = string.length();
        final char NE = (char)ESCAPE_TABLE.length;
        final String[] escapes = ESCAPE_TABLE;
        int lastPos = 0;
        int pos;
        for (pos=0; pos<N; pos++) {
            char c = string.charAt(pos);
            if (c >= NE) continue;
            String escape = escapes[c];
            if (escape == null) continue;
            if (lastPos < pos) writer.append(string, lastPos, pos);
            lastPos = pos + 1;
            writer.append(escape);
        }
        if (lastPos < pos) writer.append(string, lastPos, pos);
    }

}
