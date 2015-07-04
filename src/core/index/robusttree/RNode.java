package core.index.robusttree;

import java.util.*;

import core.access.Predicate;
import core.index.MDIndex.Bucket;
import core.index.key.MDIndexKey;
import core.utils.RangeUtils.SimpleDateRange.SimpleDate;
import core.utils.SchemaUtils.TYPE;
import core.utils.TypeUtils;

/**
 * Internal node in robust tree datastructure
 * @author anil
 */
public class RNode {

    public int attribute;
    public TYPE type;
    public Object value;
	Map<Integer, List<Object>> valuesByAttribute = new HashMap<Integer, List<Object>>();

    // Not used - left for legacy
    public float quantile;

    public RNode parent;
    public RNode leftChild;
    public RNode rightChild;

    public Bucket bucket;

    public RNode() {

    }

    @Override
	public RNode clone() {
    	RNode r = new RNode();
    	r.attribute = this.attribute;
    	r.type = this.type;
    	r.value = this.value;
    	r.quantile = this.quantile;
    	r.parent = this.parent;
    	r.leftChild = this.leftChild;
    	r.rightChild = this.rightChild;
    	r.bucket = this.bucket;
    	return r;
    }

    @Override
	public boolean equals(Object val) {
    	if (val instanceof RNode) {
    		RNode r = (RNode) val;
    		boolean allGood = true;
    		allGood &= this.attribute == r.attribute;
    		allGood &= this.type == r.type;
    		allGood &= TypeUtils.compareTo(this.value, r.value, this.type) == 0;

    		if (!allGood) return false;

    		allGood = this.leftChild == r.leftChild;
      		if (!allGood) return false;

    		allGood = this.rightChild == r.rightChild;
      		if (!allGood) return false;

      		return true;
    	}

    	return false;
    }

    public void setValues(int dimension, TYPE type, MDIndexKey key) {
        this.attribute = dimension;
        this.type = type;
        this.value = getValue(dimension, type, key);
    }

    private Object getValue(int dimension, TYPE type, MDIndexKey key) {
        switch (type) {
            case INT:
                return key.getIntAttribute(dimension);
            case LONG:
                return key.getLongAttribute(dimension);
            case FLOAT:
                return key.getFloatAttribute(dimension);
            case DATE:
                return key.getDateAttribute(dimension);
            case STRING:
                return key.getStringAttribute(dimension, 20);
            default:
                throw new RuntimeException("Unknown dimension type: "+type);
        }
    }

    private int compareKey(Object value, int dimension, TYPE type, MDIndexKey key) {
        switch (type) {
            case INT:
                return ((Integer) value).compareTo(key.getIntAttribute(dimension));
            case LONG:
                return ((Long) value).compareTo(key.getLongAttribute(dimension));
            case FLOAT:
                return ((Float) value).compareTo(key.getFloatAttribute(dimension));
            case DATE:
                return ((SimpleDate) value).compareTo(key.getDateAttribute(dimension));
            case STRING:
                return ((String) value).compareTo(key.getStringAttribute(dimension, 20));
            default:
                throw new RuntimeException("Unknown dimension type: "+type);
        }
    }

    public int getBucketId(MDIndexKey key) {
    	if (this.bucket != null) {
    		return bucket.getBucketId();
    	}
    	else {
            if (compareKey(value, attribute, type, key) >= 0) {
                return leftChild.getBucketId(key);
            }
            else {
                return rightChild.getBucketId(key);
            }
    	}
    }

    public List<RNode> search(Predicate[] ps) {
    	if (bucket == null) {
        	boolean goLeft = true;
        	boolean goRight = true;
        	for (int i = 0; i < ps.length; i++) {
        		Predicate p = ps[i];
        		if (p.attribute == attribute) {
        			switch (p.predtype) {
	                	case GEQ:
	                		if (TypeUtils.compareTo(p.value, value, type) > 0) goLeft = false;
	                		break;
	                	case LEQ:
	                		if (TypeUtils.compareTo(p.value, value, type) <= 0) goRight = false;
	                		break;
	                	case GT:
	                		if (TypeUtils.compareTo(p.value, value, type) >= 0) goLeft = false;
	                		break;
	                	case LT:
	                		if (TypeUtils.compareTo(p.value, value, type) < 0) goRight = false;
	                		break;
	                	case EQ:
	                		if (TypeUtils.compareTo(p.value, value, type) <= 0) goRight = false;
	                		else goLeft = false;
	                		break;
                	}
        		}
        	}

        	List<RNode> ret = null;
        	if (goLeft) {
        		ret = leftChild.search(ps);
        	}

        	if (goRight) {
        		if (ret == null) {
        			ret = rightChild.search(ps);
        		} else {
        			ret.addAll(rightChild.search(ps));
        		}
        	}

        	return ret;
    	} else {
    		List<RNode> ret = new LinkedList<RNode>();
    		ret.add(this);
    		return ret;
    	}
    }

    public int numTuplesInSubtree() {
    	LinkedList<RNode> stack = new LinkedList<RNode>();
    	stack.add(this);
    	int total = 0;
    	while (stack.size() > 0) {
    		RNode t = stack.removeLast();
    		if (t.bucket != null) {
    			total += t.bucket.getNumTuples();
    		} else {
    			stack.add(t.rightChild);
    			stack.add(t.leftChild);
    		}
    	}

    	return total;
    }

    public int getAll() {
    	LinkedList<RNode> stack = new LinkedList<RNode>();
    	stack.add(this);
    	int total = 0;
    	while (stack.size() > 0) {
    		RNode t = stack.removeLast();
    		if (t.bucket != null) {
    			total += t.bucket.getNumTuples();
    		} else {
    			stack.add(t.rightChild);
    			stack.add(t.leftChild);
    		}
    	}

    	return total;
    }

    public String marshall() {
    	String ret = "";
		LinkedList<RNode> stack = new LinkedList<RNode>();
		stack.add(this);
		while (stack.size() != 0) {
			RNode n = stack.removeLast();
			String nStr;
			if (n.bucket != null) {
				nStr = String.format("b %d\n", n.bucket.getBucketId());
			} else {
				nStr = String.format("n %d %s %s\n", n.attribute, n.type.toString(), TypeUtils.serializeValue(n.value, n.type));

				stack.add(n.rightChild);
				stack.add(n.leftChild);
			}
			ret += nStr;
		}
		return ret;
    }

	public void unmarshall(byte[] bytes) {
		String tree = new String(bytes);
		Scanner sc = new Scanner(tree);

		this.parseNode(sc);
	}

	public RNode parseNode(Scanner sc) {
		String type = sc.next();
		if (type.equals("n")) {
			this.attribute = sc.nextInt();
			this.type = TYPE.valueOf(sc.next());
			// For string tokens; we may have to read more than one token, so read till end of line
			this.value = TypeUtils.deserializeValue(this.type, sc.nextLine().trim());

			if (!this.valuesByAttribute.containsKey(this.attribute)) {
				this.valuesByAttribute.put(this.attribute, new ArrayList<Object>());
			}

			this.leftChild = new RNode();
			this.leftChild.parent = this;

			this.rightChild = new RNode();
			this.rightChild.parent = this;

			for (Map.Entry<Integer, List<Object>> entry : this.valuesByAttribute.entrySet()) {
				this.leftChild.valuesByAttribute.put(entry.getKey(), new ArrayList<Object>(entry.getValue()));
				this.rightChild.valuesByAttribute.put(entry.getKey(), new ArrayList<Object>(entry.getValue()));
			}

			this.leftChild.valuesByAttribute.get(this.attribute).add("<= "+this.value.toString());
			this.rightChild.valuesByAttribute.get(this.attribute).add("> " + this.value.toString());

			this.leftChild.parseNode(sc);
			this.rightChild.parseNode(sc);

		} else if (type.equals("b")) {
			Bucket b = new Bucket(sc.nextInt());
			this.bucket = b;
		} else {
			System.out.println("Bad things have happened in unmarshall");
			System.out.println(type);
		}

		return this;
	}
}
