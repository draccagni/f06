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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ManifestUtil {

	public static Manifest getJarManifest(InputStream is) throws IOException {
		ZipInputStream zis = new ZipInputStream(is);
		
		try {
			ZipEntry entry;
			while ((entry = (ZipEntry) zis.getNextEntry()) != null) {
				if (JarFile.MANIFEST_NAME.equalsIgnoreCase(entry.getName())) {
					Manifest manifest = new Manifest();
					
					manifest.read(zis);
					
					return manifest;
				}
			}
		} finally {
			zis.close();
		}
		
		return null;
	}

	public static Dictionary toDictionary(Manifest manifest) throws IOException {
		Attributes attrs = manifest.getMainAttributes();
	
		Dictionary d = new CaseSensitiveDictionary(true);
		
		for (Iterator i = attrs.entrySet().iterator(); i.hasNext();) {
			Map.Entry e = (Map.Entry) i.next();
			Attributes.Name name = (Attributes.Name) e.getKey();
			d.put(name.toString(), (String) e.getValue());
		}
	
		return d;
	}

	public static void storeManifest(Dictionary headers, OutputStream os) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
		for (Enumeration e = headers.keys(); e.hasMoreElements(); ) {
			String key = (String) e.nextElement();
			writer.write(key);
			writer.write(": ");
			writer.write((String) headers.get(key));
			writer.write("\n");
		}
		writer.flush();
	}

}
