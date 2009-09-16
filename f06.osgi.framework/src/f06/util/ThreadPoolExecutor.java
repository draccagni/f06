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


public class ThreadPoolExecutor extends ThreadExecutor {

	private ArrayBlockingQueue idleWorkers;

	private ThreadPoolWorker[] workers;

	
	public ThreadPoolExecutor(String name, int numberOfThreads) {
		super(name);
		
		if (numberOfThreads <= 1) {
			throw new IllegalArgumentException("the minimum number of threads is 2");
		}

		idleWorkers = new ArrayBlockingQueue(numberOfThreads);
		workers = new ThreadPoolWorker[numberOfThreads];
		
		shutting_down = false;
		
		for (int i = 0; i < workers.length; i++) {
			workers[i] = new ThreadPoolWorker(new StringBuilder(name).append(" Worker ").append(i).toString(), this);
			try {
				idleWorkers.add(workers[i]);
			} catch (InterruptedException e) {
				// do nothing
			}
		}
	}

	public void execute(final Runnable command) { // throws InterruptedException {
//		System.err.println("command processed by pool executor " + internalThread.getName());
		
		super.execute(new Runnable() {
			public void run() {
				try {
					ThreadPoolWorker worker = allocateWorker(); 
					worker.execute(command);
				} catch (InterruptedException e) {
					// do nothing;
				} catch (Throwable t) {
					throw new RuntimeException(t);
				}
			}
		});
	}
	
	public void shutdown() {
		try {
			for (int i = 0; i < workers.length; i++) {
				if (workers[i].isAlive()) {
					workers[i].shutdown();
					
					workers[i] = null;
				}
			}
			
			idleWorkers.discharge();
		} catch (InterruptedException x) {
			// do nothing
		}

		super.shutdown();
	}

	void releaseWorker(ThreadPoolWorker worker) throws InterruptedException {
		idleWorkers.add(worker);
	}
	
	private ThreadPoolWorker allocateWorker() throws InterruptedException {
		return (ThreadPoolWorker) idleWorkers.remove();
	}
	
	public boolean isIdle() {
		return idleWorkers.isFull();
	}
	
	public void printStatistics() {
		for (int i = 0; i < workers.length; i++) {
			System.err.println("Worker " + i + " has processed " + workers[i].getProcessedCommandNumber() + " commands");
		}
	}
	
	public static void main(String[] args) throws Exception {
		ThreadPoolExecutor executor = new ThreadPoolExecutor("Test", 4);
		
		for (int i = 0; i < 10000; i++) {
			final int I = i;
			executor.execute(new Runnable() {
				public void run() {
					int j = I;
				}
			});
		}
		
		while (!executor.isIdle()) {
			Thread.sleep(3000);
		}
		
		executor.printStatistics();
	}
}
