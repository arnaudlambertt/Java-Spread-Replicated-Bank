import spread.AdvancedMessageListener;
import spread.SpreadException;
import spread.SpreadMessage;

public class Listener implements AdvancedMessageListener {
    public void regularMessageReceived(SpreadMessage message) {
        Transaction transaction = null;
        String msg = null;
        try {
            //transaction = (Transaction) message.getObject();
            msg = (String)message.getObject();
            System.out.println(msg);
            //scanner first element of command
            //switch first element as method
            //CHECK IF NOT ALREADY EXECUTED, iF YES => ALERT
            //execute it
            //PUT IT IN THE EXECUTED COLLECTION
            //REMOVE FROM OUTSTANDING
            //IF EMPTY, NOTIFY OUTSTANDING COLLECTION (FOR CLIENT WAIT)

        } catch (SpreadException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void membershipMessageReceived(SpreadMessage spreadMessage) {
        System.out.println(spreadMessage.getMembershipInfo().getMembers());
    }

}