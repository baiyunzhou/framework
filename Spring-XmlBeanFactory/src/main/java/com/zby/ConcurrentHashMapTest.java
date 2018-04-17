package com.zby;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentHashMapTest {

	public static void main(String[] args) {
		Map<String, String> map = new ConcurrentHashMap<String, String>();
		map.put("a", null);
	}

}
