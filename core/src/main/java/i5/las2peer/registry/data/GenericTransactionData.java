package i5.las2peer.registry.data;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.web3j.utils.Convert;
import net.minidev.json.JSONObject;


/* 
 * 
 * Represents the transaction with its message in the reputation smart contract
 * @see ReputationRegistry.addGenericTransaction
 */
public class GenericTransactionData {
    private String sender;
    private String receiver;

    private BigInteger amountInWei;
    private String message;
    private BigInteger timestamp;
    private String txHash;
    private String transactionType;

    public GenericTransactionData(
        String sender,
        String receiver,
        BigInteger amountInWei,
        BigInteger timestamp,
        String message,
        String transactionType,
        String txHash
    ) {
        this.setSender(sender);
        this.setReceiver(receiver);
        this.setAmountInWei(amountInWei);
        this.setTimestamp(timestamp);
        this.setMessage(message);
        this.setTXHash(txHash);
        this.setTransactionType(transactionType);
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public BigInteger getTimestamp() {
        return timestamp;
    }

    // https://stackoverflow.com/a/31112092
    public String getTime() {
        Instant instant = Instant.ofEpochMilli(timestamp.longValue());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return fmt.format(instant.atZone(ZoneId.systemDefault()));
    }

    public String getTXHash() {
        return txHash;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public String getMessage() {
        return message;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setTimestamp(BigInteger timestamp) {
        this.timestamp = timestamp;
    }

    public void setTXHash(String txHash) {
        this.txHash = txHash;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public BigInteger getAmountInWei() {
        return amountInWei;
    }

    public String getAmountInEth() {
        return Convert.fromWei(amountInWei.toString(), Convert.Unit.ETHER).toString();
    }

    public void setAmountInWei(BigInteger amountInWei) {
        this.amountInWei = amountInWei;
    }

    public JSONObject toJSONObject() {
        JSONObject thisJSON = new JSONObject();

        thisJSON.put("txSender", this.getSender());
        thisJSON.put("txReceiver", this.getReceiver());
        thisJSON.put("txMessage", this.getMessage());
        thisJSON.put("txAmountInWei", this.getAmountInWei() );
        thisJSON.put("txAmountInEth", this.getAmountInEth());
        thisJSON.put("txTXHash", this.getTXHash() );
        thisJSON.put("txTimestamp", this.getTimestamp() );
        thisJSON.put("txDateTime", this.getTime());
        thisJSON.put("txTransactionType", this.getTransactionType() );

        return thisJSON;
    }
}