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

import java.util.ArrayList;
import java.util.List;

public class ThreadExecutor implements Runnable {

	protected List queue;
	
	protected Thread internalThread;
	
	protected Object executionLock;
	
	protected volatile boolean shutting_down;

	public ThreadExecutor(String name) {
		queue = new ArrayList();
		
		executionLock = new Object();
		
		internalThread = new Thread(this, name);
		
		internalThread.setDaemon(true);
		
		internalThread.start();
	}

	public void execute(Runnable command) {
		if (shutting_down) {
			System.err.println("WARNING: thread pool is shutting down, the command cannot be execute.");
			return;
		}

		synchronized (queue) {
			queue.add(command);
			
			queue.notifyAll();
		}
	}

	public void shutdown() {
		if (!shutting_down) {
			shutting_down = true;
				
			synchronized (executionLock) {
				internalThread.interrupt();
				
				try {
					internalThread.join();
				} catch (InterruptedException e) {
					// do nothing
				}

				internalThread = null;
			}
		}
	}

	final public void run() {
		while (!shutting_down) {
			Runnable command = null;
			synchronized (queue) {
				// prevent a misalignment between wait/notifyAll
				while (queue.isEmpty()) {
					try {
						queue.wait();
					} catch (InterruptedException ex) {
						return;
					}
				}

				command = (Runnable) queue.remove(0);
			}

			synchronized (executionLock) {
				command.run();
			}
		}
	}

	public boolean isShuttingDown() {
		return shutting_down;
	}
}
