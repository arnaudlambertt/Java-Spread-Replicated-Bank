import java.util.Objects;

public class Transaction{

    final String command;
    final String uniqueId;

    /**
     * Initialize transaction
     * @param command Method + Arguments
     * @param uniqueId UUID
     * @throws NoSuchMethodException when the method does not exist
     * @throws IllegalArgumentException when arguments are not doubles
     */
    public Transaction(String command, String uniqueId) throws NoSuchMethodException, IllegalArgumentException {
        this.command = command;
        String method = command.split(" ")[0];
        String args = command.split(" ")[1];

        switch (method) {
            case "deposit":
            case "withdraw":
            case "addInterest":
                break;
            default:
                throw new NoSuchMethodException("No such method: " + method);
        }

        try {
            Double.parseDouble(args);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad arguments: " + args);
        }

        this.uniqueId = uniqueId;
    }

    public Transaction(String input) throws NoSuchMethodException, IllegalArgumentException {
        this(input.split(" ")[0] + " " + input.split(" ")[1], input.split(" ")[2]);
    }

    @Override
    public String toString() {
        return command + " " + uniqueId;
    }

    /**
     * Check if two transactions are identical
     * @param o external object
     * @return true if equal
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(uniqueId, that.uniqueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uniqueId);
    }
}
