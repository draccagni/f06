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

public class Future implements Runnable {
	
	private volatile boolean task_completed = false;
	
	private Object task_lock = new Object();
	
	private Runnable runnable;
	
	public Future(Runnable inner) {
		this.runnable = inner;
	}
	
	public void run() {
		synchronized (task_lock) {
			runnable.run();

			task_completed = true;
			
			task_lock.notifyAll();
		}
	}
	
	public Runnable get() {
		synchronized (task_lock) {
			if (!task_completed) {
				try {
					task_lock.wait();
				} catch (InterruptedException e) {
				}
			}
		}
		
		return runnable;
	}
}

