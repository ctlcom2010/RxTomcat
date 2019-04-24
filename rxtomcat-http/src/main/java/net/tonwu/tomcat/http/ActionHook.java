package net.tonwu.tomcat.http;


/**
 * 容器对 Processor 请求操作的回调机制
 */
public interface ActionHook {

    public enum ActionCode {
        ACK,
        CLOSE,
        COMMIT
    }

    /**
     * Send an action to the connector.
     * 
     * @param actionCode Type of the action
     * @param param Action parameter
     */
    public void action(ActionCode actionCode, Object... param);

}
