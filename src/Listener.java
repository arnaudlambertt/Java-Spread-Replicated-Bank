import spread.AdvancedMessageListener;
import spread.SpreadException;
import spread.SpreadMessage;

public class Listener implements AdvancedMessageListener {
    /**
     * On message received from spread server
     * @param message Received regular message
     */
    public void regularMessageReceived(SpreadMessage message) {
        Transaction transaction = null;
        String msg;
        try {
            msg = (String) message.getObject();
            if(msg.contains("updateBalance")){
                if(msg.split(" ")[1].contains(AccountReplica.id.toString().substring(0,10)) && !AccountReplica.balanceUpdated) {
                    try{
                        AccountReplica.balance += Double.parseDouble(msg.split(" ")[2]);
                        AccountReplica.balanceUpdated = true;
                        System.out.println("Balance successfully initialized. Balance = " + AccountReplica.balance);
                    }catch(NumberFormatException e){
                        System.err.println("Failed initializing balance");
                    }
                }
                return;
            }
            try {
                transaction = new Transaction(msg);
            } catch (NoSuchMethodException | IllegalArgumentException e) {
                System.err.println(e.getMessage());
            }

            assert transaction != null;
            String method = transaction.command.split(" ")[0];
            double argument = Double.parseDouble(transaction.command.split(" ")[1]);

            if(!AccountReplica.executedList.contains(transaction)){

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
                    default:
                }
                System.out.println("Executed transaction " + transaction);

            }else
                System.out.println(transaction + " already executed");

            if(AccountReplica.outstandingCollection.contains(transaction)){
                Transaction outstandingTransaction = AccountReplica.outstandingCollection.get(AccountReplica.outstandingCollection.indexOf(transaction));

                synchronized (outstandingTransaction) {
                    if (AccountReplica.outstandingCollection.remove(outstandingTransaction))
                        outstandingTransaction.notifyAll();
                }
            }

        } catch (SpreadException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * When a member joins/leaves the SpreadGroup
     * @param spreadMessage Received membership message
     */
    @Override
    public void membershipMessageReceived(SpreadMessage spreadMessage) {
        AccountReplica.membersInfo = spreadMessage.getMembershipInfo().getMembers();
        synchronized (AccountReplica.group){
            if(AccountReplica.membersInfo.length == AccountReplica.numberOfReplicas) {
                AccountReplica.group.notify();
            }
        }
        if(AccountReplica.membersInfo.length > AccountReplica.numberOfReplicas){ //only executes when new client joins
            SpreadMessage message = new SpreadMessage();
            message.addGroup(AccountReplica.group);
            message.setFifo();
            try {
                message.setObject("updateBalance " + spreadMessage.getMembershipInfo().getJoined() + " " + AccountReplica.balance);
                AccountReplica.connection.multicast(message);
            } catch (SpreadException e) {
                throw new RuntimeException(e);
            }
        }
        if (AccountReplica.isInitialized){
            AccountReplica.numberOfReplicas = AccountReplica.membersInfo.length;
        }
    }
}