package com.transferwise.common.entrypoints;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
@Accessors(chain = true)
public class EntryPointContext {
    public static String GROUP_GENERIC = "Generic";

    private String name;
    private String group;

    private Map<String, Object> attributes;

    public EntryPointContext(String group, String name) {
        this.name = name;
        this.group = StringUtils.trimToNull(group) == null ? GROUP_GENERIC : group;
        attributes = new ConcurrentHashMap<>();
    }

}
