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
