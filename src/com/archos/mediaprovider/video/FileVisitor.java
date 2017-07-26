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

package com.archos.mediaprovider.video;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.ftp.AuthenticationException;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;


import java.io.IOException;
import java.util.List;

public class FileVisitor {

    public interface Listener {
        /** called when visiting started */
        void onStart(MetaFile2 root);
        /** called for every directory, return true if this directory should be visited */
        boolean onDirectory(MetaFile2 directory);
        /** called for every file */
        void onFile(MetaFile2 file);
        /** called for every directory with a list of children files, return true if this directory should be indexed */
        boolean onFilesList(List<MetaFile2> files);
        /** called for everyhing not file or directory */
        void onOtherType(MetaFile2 file);
        /** called when visiting finished */
        void onStop(MetaFile2 root);
    }

    private FileVisitor() {
        /* empty */
    }

    public static void visit(MetaFile2 root, int recursionLimit, Listener listener) {
        if (listener != null) {
            listener.onStart(root);
            /*
                before starting to visit, we should check if a parent of this file is within a folder with a .nomedia
             */
                recurse(root, listener, recursionLimit);
            listener.onStop(root);
        }
    }


    private static void recurse(MetaFile2 file, Listener listener, int recursionLimit) {
        if (recursionLimit < 0) {
            return;
        }

        if (file != null) {
            if (file.isDirectory()) {
                if (listener.onDirectory(file) && recursionLimit > 0) {
                    try {
                        List<MetaFile2> files  = file.getRawListerInstance().getFileList();
                            if (files != null) {
                                if (listener.onFilesList(files))
                                for (MetaFile2 subFile : files) {
                                    recurse(subFile, listener, recursionLimit - 1);
                                }
                            }
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
            } else if (file.isFile()) {
                listener.onFile(file);
            } else {
                listener.onOtherType(file);
            }
        }
    }
}
