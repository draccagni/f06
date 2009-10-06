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

public class SerialExecutorService implements Runnable {
	
	protected List queue;
	
	protected Thread internalThread;
	
	protected volatile boolean shutting_down;

	protected volatile boolean terminated;
	
	public SerialExecutorService(String name) {
		queue = new ArrayList();
		
		shutting_down = false;
		
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
	
	public Future submit(Runnable command) {
		Future future = new Future(command);
		execute(future);
		
		return future;
	}

	public void shutdown() {
		if (!shutting_down) {
			synchronized (queue) {
				shutting_down = true;

				internalThread.interrupt();
				
				internalThread = null;
			}
		}
	}

	final public void run() {
		EXIT: while (!shutting_down) {
			Runnable command = null;
			synchronized (queue) {
				// to prevent a misalignment between wait/notifyAll
				while (queue.isEmpty()) {
					try {
						queue.wait();
					} catch (InterruptedException ex) {
						// the executor has been shutted down
						break EXIT;
					}
				}

				command = (Runnable) queue.remove(0);

				command.run();
			}
		}
	
		terminated = true;
	}
}
