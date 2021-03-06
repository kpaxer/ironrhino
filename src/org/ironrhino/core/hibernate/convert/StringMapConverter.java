package org.ironrhino.core.hibernate.convert;

import java.util.Map;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import org.ironrhino.core.util.JsonUtils;

@Converter
public class StringMapConverter implements AttributeConverter<Map<String, String>, String> {

	@Override
	public String convertToDatabaseColumn(Map<String, String> map) {
		if (map == null)
			return null;
		return JsonUtils.toJson(map);
	}

	@Override
	public Map<String, String> convertToEntityAttribute(String string) {
		if (string == null)
			return null;
		try {
			return JsonUtils.fromJson(string, JsonUtils.STRING_MAP_TYPE);
		} catch (Exception e) {
			throw new IllegalArgumentException(string + " is not valid json ", e);
		}
	}

}