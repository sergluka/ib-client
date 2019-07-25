package com.finplant.ib.types;

public class IbExecutionReport {

    private IbContract contract;
    private IbExecution execution;
    private IbCommissionReport commission;

    public IbExecutionReport(IbContract contract, IbExecution execution) {
        this.contract = contract;
        this.execution = execution;
    }

    public IbContract getContract() {
        return contract;
    }

    public IbExecution getExecution() {
        return execution;
    }

    public IbCommissionReport getCommission() {
        return commission;
    }

    public void setCommission(IbCommissionReport commission) {
        this.commission = commission;
    }

    @Override
    public String toString() {
        final StringBuffer buffer = new StringBuffer("{");
        buffer.append("contract=").append(contract);
        buffer.append(", execution=").append(execution);
        buffer.append(", commission=").append(commission);
        buffer.append('}');
        return buffer.toString();
    }
}
