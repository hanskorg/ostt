package org.hansk.tools.transfer.storage;

import com.qcloud.cos.exception.CosServiceException;
import com.sun.org.apache.bcel.internal.classfile.Unknown;
import org.hansk.tools.transfer.domain.Transfer;

/**
 *
 * @author guohao
 * @date 2018/10/22
 */
public class TransferException extends Exception {
    private static final long serialVersionUID = 1L;
    private String errorMessage;
    private int transferStatus;
    private Transfer transfer;
    private ErrorType errorType ;

    public TransferException(){
        super((String)null, null);
        this.errorType = ErrorType.Unknown;
    }
    public  TransferException(String errorMessage, Exception cause){
        super((String)errorMessage, cause);
        this.errorType = ErrorType.Unknown;
        this.errorMessage = errorMessage;
    }

    public  TransferException(ErrorType errorType, String errorMessage, Exception cause){
        super((String)errorMessage, cause);
        this.errorType = ErrorType.Unknown;
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getTransferStatus() {
        return transferStatus;
    }

    public void setTransferStatus(int transferStatus) {
        this.transferStatus = transferStatus;
    }

    public Transfer getTransfer() {
        return transfer;
    }

    public void setTransfer(Transfer transfer) {
        this.transfer = transfer;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public void setErrorType(ErrorType errorType) {
        this.errorType = errorType;
    }

    public static enum ErrorType {
        NotFound,
        DownLoadError,
        UploadError,
        Unknown;

        private ErrorType() {
        }
    }
}

