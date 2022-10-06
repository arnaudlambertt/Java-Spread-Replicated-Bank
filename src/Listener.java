import spread.AdvancedMessageListener;
import spread.SpreadException;
import spread.SpreadMessage;

import java.util.Arrays;

public class Listener implements AdvancedMessageListener {
    public void regularMessageReceived(SpreadMessage message) {
        Transaction transaction = null;
        String msg = null;
        try {
            msg = (String) message.getObject();
            try {
                transaction = new Transaction(msg);
            } catch (NoSuchMethodException | IllegalArgumentException e) {
                System.err.println(e.getMessage());
            }

            String method = transaction.command.split(" ")[0];
            double argument = Double.parseDouble(transaction.command.split(" ")[1]);

            if(!AccountReplica.executedList.contains(transaction)){

                System.out.println(transaction);
                AccountReplica.executedList.add(transaction);
                AccountReplica.orderCounter++;

                switch (method){
                    case "deposit":{
                        AccountReplica.deposit(argument);
                        break;
                    }
                    case "addInterest":{
                        AccountReplica.addInterest(argument);
                        break;
                    }
                    case "withdraw":{
                        AccountReplica.withdraw(argument);
                        break;
                    }
                    default:;
                }

            }else
                System.out.println(transaction + " already executed");

            synchronized (AccountReplica.outstandingCollection) {
                if (AccountReplica.outstandingCollection.remove(transaction) && AccountReplica.outstandingCollection.isEmpty())
                    AccountReplica.outstandingCollection.notify();
            }

        } catch (SpreadException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void membershipMessageReceived(SpreadMessage spreadMessage) {
        System.out.println(Arrays.toString(spreadMessage.getMembershipInfo().getMembers()));
        AccountReplica.membersInfo = spreadMessage.getMembershipInfo().getMembers();
        synchronized (AccountReplica.group){
            if(AccountReplica.membersInfo.length == AccountReplica.numberOfReplicas)
                AccountReplica.group.notify();
        }
    }
}