/*
 * Copyright (c) Davide Raccagni (2006, 2009). All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package f06.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IOUtil {

	public static void delete(File fileDir) throws IOException {
		File[] files = fileDir.listFiles();
		if (files != null) {
			for (int i = 0; i < files.length; i++) {
				File file = files[i];
				if (file.isDirectory()) {
					delete(file);
				} else {
					if (!file.delete()) {
						throw new IOException(new StringBuilder("Cannot delete ").append(fileDir.getAbsolutePath()).toString());
					}				
				}
			}
		}
		
		if (!fileDir.delete()) {
			throw new IOException(new StringBuilder("cannot delete ").append(fileDir.getAbsolutePath()).toString());
		}
	}
	
    public static byte[] getBytes(InputStream is) throws IOException {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream(is.available());
        byte[] buf = new byte[4096];
        int n = 0;
        while ((n = is.read(buf, 0, buf.length)) >= 0)
        {
            baos.write(buf, 0, n);
        }
        
        is.close();

	    return baos.toByteArray();
    }

    public static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[4096];
        int n = 0;
        while ((n = is.read(buf, 0, buf.length)) >= 0)
        {
            os.write(buf, 0, n);
        }
    }
    
	public static void store(File file, byte[] bytes) throws IOException {
		FileOutputStream fos = new FileOutputStream(file);					
		fos.write(bytes);
		fos.close();
	}
}
