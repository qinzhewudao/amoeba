package core.common.key;

import com.google.common.base.Joiner;

import core.common.globals.Globals;
import core.utils.TypeUtils.SimpleDate;
import core.utils.TypeUtils.TYPE;

public class ParsedIndexKey extends RawIndexKey {

	private Object[] values;

	public ParsedIndexKey(TYPE[] types) {
		super(Globals.DELIMITER); // some dummy delimiter (not used really)
		this.types = types;
	}

	public void setValues(Object[] values) {
		this.values = values;
	}

	@Override
	public String getKeyString() {
		return Joiner.on(",").join(values);
	}

	@Override
	public String getStringAttribute(int index, int maxSize) {
		return (String) values[index];
	}

	@Override
	public int getIntAttribute(int index) {
		return (Integer) values[index];
	}

	@Override
	public long getLongAttribute(int index) {
		return (Long) values[index];
	}

	@Override
	public float getFloatAttribute(int index) {
		return (Float) values[index];
	}

	@Override
	public double getDoubleAttribute(int index) {
		return (Double) values[index];
	}

	@Override
	public SimpleDate getDateAttribute(int index) {
		return (SimpleDate) values[index];
	}

	@Override
	public SimpleDate getDateAttribute(int index, SimpleDate date) {
		return getDateAttribute(index);
	}

	@Override
	public boolean getBooleanAttribute(int index) {
		return (Boolean) values[index];
	}
}