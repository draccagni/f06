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

class ArrayBlockingQueue extends Object {
	private Object[] queue;

	private int capacity;

	private int size;

	private int head;

	private int tail;

	public ArrayBlockingQueue(int capacity) {
		// at least 1
		if (capacity < 1) {
			throw new IllegalArgumentException("");
		}

		this.capacity = capacity;
		queue = new Object[this.capacity];
		head = 0;
		tail = 0;
		size = 0;
	}

	public int getCapacity() {
		return this.capacity;
	}

	public synchronized int getSize() {
		return this.size;
	}

	public synchronized boolean isEmpty() {
		return this.size == 0;
	}

	public synchronized boolean isFull() {
		return this.size == this.capacity;
	}

	public synchronized void add(Object obj) throws InterruptedException {
		while (isFull()) {
			wait();
		}

		queue[head] = obj;
		head = (head + 1) % capacity;
		size++;

		notifyAll();
	}

	public synchronized Object remove() throws InterruptedException {
		while (isEmpty()) {
			wait();
		}

		Object obj = queue[tail];

		queue[tail] = null;

		tail = (tail + 1) % capacity;
		size--;

		notifyAll();

		return obj;
	}

	public synchronized Object[] removeAll() throws InterruptedException {
		Object[] list = new Object[size];

		for (int i = 0; i < list.length; i++) {
			list[i] = remove();
		}

		return list;
	}

	public synchronized void discharge() throws InterruptedException {
		head = 0;
		tail = 0;
		size = 0;

		notifyAll();
	}
}
