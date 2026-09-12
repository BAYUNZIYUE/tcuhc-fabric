/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc.options;

import com.google.common.collect.ImmutableSet;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;

public abstract class OptionType {
	
	protected Object value;

	protected static String getDisplayString(Object value) {
		if (value instanceof Boolean) {
			return (boolean) value ? "开启" : "关闭";
		}
		if (value instanceof Enum) {
			String enumName = ((Enum) value).name();
			switch (enumName) {
				case "PEACEFUL": return "和平";
				case "EASY": return "简单";
				case "NORMAL": return "普通";
				case "HARD": return "困难";
				default: break;
			}
		}
		return value.toString();
	}
	
	public abstract String getIncString();
	public abstract String getDecString();
	public abstract void applyInc();
	public abstract void applyDec();
	public abstract void setValue(Object nvalue);
	public abstract void setStringValue(String nvalue);
	public Object getValue() { return value; }
	public String getStringValue() { return getDisplayString(getValue()); }
	
	/**
	 * Explains why {@link #setStringValue} would not take this raw value, or returns null when it
	 * would. Needed by the preset loader, because setStringValue has no failure signal of its own -
	 * EnumType quietly keeps the old value when the lookup misses, and NumbericType clamps an out
	 * of range value instead of rejecting it, so both would otherwise look like a success.
	 */
	public String validateStringValue(String rawValue) { return null; }
	
	@Override
	public String toString() {
		return getStringValue();
	}
	
	static abstract class NumbericType<T> extends OptionType {
		
		protected T min, max, step;
		private String inc, dec;
		
		public NumbericType(T min, T max, T step) {
			this.min = min;
			this.max = max;
			this.step = step;
			inc = "+ " + step;
			dec = "- " + step;
		}
		
		@Override public String getIncString() { return inc; }
		@Override public String getDecString() { return dec; }
		
	}
	
	public static class IntegerType extends NumbericType<Integer> {
		
		public IntegerType(int min, int max, int step) {
			super(min, max, step);
			value = 0;
		}
		@Override public void applyInc() { value = Math.min(max, (int) value + step); }
		@Override public void applyDec() { value = Math.max(min, (int) value - step); }
		@Override public void setValue(Object nvalue) { value = Math.min(max, Math.max(min, (int) nvalue)); }
		@Override public void setStringValue(String nvalue) { setValue(Integer.parseInt(nvalue)); }
		@Override public String validateStringValue(String rawValue) {
			try {
				int parsed = Integer.parseInt(rawValue);
				return parsed < min || parsed > max ? "超出范围 " + min + "~" + max : null;
			} catch (NumberFormatException e) {
				return "不是整数";
			}
		}
		
	}
	
	public static class FloatType extends NumbericType<Float> {
		
		public FloatType(float min, float max, float step) {
			super(min, max, step);
			value = 0.0f;
		}
		@Override public void applyInc() { value = Math.min(max, (float) value + step); }
		@Override public void applyDec() { value = Math.max(min, (float) value - step); }
		@Override public void setValue(Object nvalue) { value = Math.min(max, Math.max(min, (float) nvalue)); }
		@Override public void setStringValue(String nvalue) { setValue(Float.parseFloat(nvalue)); }
		@Override public String validateStringValue(String rawValue) {
			try {
				float parsed = Float.parseFloat(rawValue);
				return parsed < min || parsed > max ? "超出范围 " + min + "~" + max : null;
			} catch (NumberFormatException e) {
				return "不是数字";
			}
		}
		
	}
	
	public static class BooleanType extends OptionType {
		
		/** Exactly the tokens {@link #setStringValue} recognises, in its own spelling. */
		private static final Set<String> TOKENS = ImmutableSet.of(
				"true", "false", "开启", "关闭", "开", "关", "是", "否"
		);
		
		public BooleanType() {
			value = false;
		}
		@Override public String getIncString() { return "开启"; }
		@Override public String getDecString() { return "关闭"; }
		@Override public void applyInc() { value = true; }
		@Override public void applyDec() { value = false; }
		@Override public void setValue(Object nvalue) { value = (boolean) nvalue; }
		@Override public void setStringValue(String nvalue) {
			setValue("true".equalsIgnoreCase(nvalue) || "开启".equals(nvalue) || "开".equals(nvalue) || "是".equals(nvalue));
		}
		@Override public String validateStringValue(String rawValue) {
			// Anything else silently becomes false, which is how a typo turns a switch off unnoticed.
			return TOKENS.contains(rawValue.toLowerCase(Locale.ROOT)) ? null : "只接受 开启 / 关闭";
		}
		
	}
	
	public static class EnumType extends OptionType {
		
		private final Class enumClass;
		private Object[] enums;

		private Object parseEnumValue(String rawValue) {
			try {
				return Enum.valueOf(enumClass, rawValue);
			} catch (IllegalArgumentException ignored) {
			}
			for (Object enumValue : enums) {
				Enum<?> enumConstant = (Enum<?>) enumValue;
				if (enumConstant.name().equalsIgnoreCase(rawValue)) {
					return enumValue;
				}
				if (enumConstant.toString().equals(rawValue) || getDisplayString(enumValue).equals(rawValue)) {
					return enumValue;
				}
			}
			return null;
		}
		
		private Method getMethod(Class clazz, String func, Class ... params) {
			try {
				return clazz.getMethod(func, params);
			} catch (IllegalArgumentException | NoSuchMethodException | SecurityException e) {
				e.printStackTrace();
			}
			return null;
		}
		
		private Object invokeMethod(Method method, Object obj, Object ... params) {
			try {
				return method.invoke(obj, params);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
			}
			return null;
		}
		
		public EnumType(Class enums) {
			enumClass = enums;
			this.enums = (Object[]) invokeMethod(getMethod(enums, "values"), null);
			value = 0;
		}
		@Override public String getIncString() { return getDisplayString(enums[((int) value + 1) % enums.length]); }
		@Override public String getDecString() { return getDisplayString(enums[((int) value + enums.length - 1) % enums.length]); }
		@Override public void applyInc() { value = ((int) value + 1) % enums.length; }
		@Override public void applyDec() { value = ((int) value + enums.length - 1) % enums.length; }
		@Override public void setValue(Object nvalue) {
			for (int i = 0; i < enums.length; i++) {
				if (enums[i].equals(nvalue)) {
					value = i;
					break;
				}
			}
		}
		@Override public Object getValue() { return enums[(int) value]; }
		@Override public void setStringValue(String nvalue) { setValue(parseEnumValue(nvalue)); }
		@Override public String validateStringValue(String rawValue) {
			if (parseEnumValue(rawValue) != null) {
				return null;
			}
			StringBuilder accepted = new StringBuilder();
			for (Object enumValue : enums) {
				accepted.append(getDisplayString(enumValue)).append(' ');
			}
			return "只能取 " + accepted.toString().trim();
		}
		
	}
	
}
