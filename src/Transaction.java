import java.util.Objects;

public class Transaction {
    String command = "";
    String uniqueId = "";

    public Transaction(String command, String uniqueId){
        this.command = command;
        this.uniqueId = uniqueId;
    }

    public Transaction(String input){
        this(input.split(" ")[0] + " " + input.split(" ")[1], input.split(" ")[2]);
    }

    @Override
    public String toString() {
        return command + " " + uniqueId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(command, that.command) && Objects.equals(uniqueId, that.uniqueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, uniqueId);
    }
}
