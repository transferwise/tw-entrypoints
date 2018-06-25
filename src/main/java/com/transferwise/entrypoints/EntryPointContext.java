package com.transferwise.entrypoints;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Data
@Accessors(chain = true)
public class EntryPointContext {
	private String name;

	private Map<String, Object> attributes;

	public EntryPointContext(String name) {
		this.name = name;
		attributes = new ConcurrentHashMap<>();
	}

}
