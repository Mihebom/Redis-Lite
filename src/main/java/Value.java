import java.util.LinkedList;
import java.util.Set;
import java.util.SortedSet;

//This class represents the value of a key in the Redis database
public class Value {


    private String type;

    private LinkedList<String> list;

    private Set<String> set;

    private SortedSet<String> sortedSet;

    private String value;


    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LinkedList<String> getList() {
        return list;
    }

    public void setList(LinkedList<String> list) {
        this.list = list;
    }

    public Set<String> getSet() {
        return set;
    }

    public void setSet(Set<String> set) {
        this.set = set;
    }

    public SortedSet<String> getSortedSet() {
        return sortedSet;
    }

    public void setSortedSet(SortedSet<String> sortedSet) {
        this.sortedSet = sortedSet;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "String: " + value + " List: "  + list;
    }


    public boolean compareList(Value val){
        return this.list.equals(val.getList());
    }

    public boolean compareValue(Value val){
        return this.value.equals(val.getValue());
    }

    public boolean compareSet(Value val){
        return this.set.equals(val.getSet());
    }

    public boolean compareSortedSet(Value val){
        return this.sortedSet.equals(val.getSortedSet());
    }

}
