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


class ThreadPoolWorker implements Runnable {
	
	private Runnable command;
	
	private Object commandLock;

	private Thread internalThread;

	private ThreadPoolExecutor threadPool;

	private volatile boolean shutting_down;
	
	protected Object executionLock;
	
	private int processedCommandNumber;

	ThreadPoolWorker(String name, ThreadPoolExecutor threadPool) {
		this.threadPool = threadPool;
		
		this.commandLock = new Object();
		
		this.executionLock = new Object();

		this.shutting_down = false;

		this.internalThread = new Thread(this, name);
		// If the executor invoker thread terminates, worker will terminate, too
		this.internalThread.setDaemon(true);
		
		this.internalThread.start();
	}
	
	void execute(Runnable command) throws InterruptedException {
		synchronized (commandLock) {
			this.command = command;
			
			this.commandLock.notifyAll();
		}
	}

	public void run() {
		while (!shutting_down) {
			try {
				synchronized (this.commandLock) {
					while (this.command == null) {
						try {
							this.commandLock.wait();
						} catch (InterruptedException ex) {
							return;
						}
					}
				
					synchronized (executionLock) {
						this.command.run();
					}
					
					this.command = null;
					
					this.processedCommandNumber++;
					
					this.threadPool.releaseWorker(this);
				}
			} catch (InterruptedException x) {
				// do nothing
			}
		}
	}

	void shutdown() throws InterruptedException {
		if (!shutting_down) {
			shutting_down = true;
				
			synchronized (executionLock) {
				internalThread.interrupt();

				internalThread = null;
			}
		}
	}

	boolean isAlive() {
		return this.internalThread.isAlive();
	}
	
	int getProcessedCommandNumber() {
		return this.processedCommandNumber;
	}
}