public class Transaction{

    final String command;
    final String uniqueId;

    public Transaction(String command, String uniqueId) throws NoSuchMethodException, IllegalArgumentException {
        this.command = command;
        String method = command.split(" ")[0];
        String args = command.split(" ")[1];

        switch(method){
            case "deposit":
            case "withdraw":
            case "addInterest":
                break;
            default:
                throw new NoSuchMethodException("No such method: " + method);
        }

        try{
            Double.parseDouble(args);
        }
        catch (NumberFormatException e){
            throw new IllegalArgumentException("Bad arguments: " + args);
        }

        this.uniqueId = uniqueId;
    }

    @Override
    public String toString() {
        return command + " " + uniqueId;
    }
}
