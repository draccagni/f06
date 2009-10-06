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
package f06.osgi.framework.launch;

import java.util.Map;

import org.osgi.framework.launch.Framework;

public class FrameworkFactory implements org.osgi.framework.launch.FrameworkFactory {

	public Framework newFramework(Map configuration) {
		Framework framework = new f06.osgi.framework.Framework(configuration);
		
		return framework;
	}
}
