package company;

import contracts.*;
import objects.Person;
import objects.Vehicle;
import payment.ContractPaymentData;
import payment.PaymentHandler;
import payment.PremiumPaymentFrequency;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

public class InsuranceCompany {
    private final Set<AbstractContract> contracts;
    private final PaymentHandler handler;
    private LocalDateTime currentTime;

    public InsuranceCompany(LocalDateTime currentTime){
        if(currentTime == null)
            throw new IllegalArgumentException("currentTime must not be null.");

        this.currentTime = currentTime;
        this.contracts = new LinkedHashSet<>();   //Zmluvy sú v množine zmlúv uložené v takom poradí, v akom ich poisťovňa uzatvárala
        this.handler = new PaymentHandler(this);
    }
    public LocalDateTime getCurrentTime(){
        return currentTime;
    }

    public void setCurrentTime(LocalDateTime currentTime) {
        if(currentTime == null)
            throw new IllegalArgumentException("currentTime must not be null.");

        this.currentTime = currentTime;
    }

    public Set<AbstractContract> getContracts(){
        return contracts;
    }

    public PaymentHandler getHandler(){
        return handler;
    }

    public SingleVehicleContract insureVehicle(String contractNumber, Person beneficiary, Person policyHolder, int proposedPremium, PremiumPaymentFrequency proposedPaymentFrequency, Vehicle vehicleToInsure){
        if (contractNumber == null || contractNumber.isEmpty())
            throw new IllegalArgumentException("contractNumber must not be null or empty.");
        if (proposedPaymentFrequency == null)
            throw new IllegalArgumentException("proposedPaymentFrequency must not be null.");
        if (vehicleToInsure == null)
            throw new IllegalArgumentException("vehicleToInsure must not be null.");
        if (proposedPremium <= 0)
            throw new IllegalArgumentException("proposedPremium must be positive.");

        for(AbstractContract contract : contracts){
            if (contractNumber.equals(contract.getContractNumber()))
                throw new IllegalArgumentException("contractNumber must be unique.");
        }

        int valueInMonths = proposedPaymentFrequency.getValueInMonths();
        int yearSum = proposedPremium * (12/valueInMonths);

        if(yearSum < vehicleToInsure.getOriginalValue() * 0.02)
            throw new IllegalArgumentException("Proposed premium does not meet minimum yearly threshold.");

        ContractPaymentData paymentData = new ContractPaymentData(proposedPremium, proposedPaymentFrequency, currentTime, 0);
        SingleVehicleContract newContract = new SingleVehicleContract(contractNumber, this, beneficiary, policyHolder, paymentData, vehicleToInsure.getOriginalValue()/2, vehicleToInsure);

        chargePremiumOnContract(newContract);

        contracts.add(newContract);
        policyHolder.addContract(newContract);

        return newContract;
    }

    public TravelContract insurePersons(String contractNumber, Person policyHolder, int proposedPremium, PremiumPaymentFrequency proposedPaymentFrequency, Set<Person> personsToInsure){
        if (contractNumber == null || contractNumber.isEmpty())     //asi nemusi byť
            throw new IllegalArgumentException("contractNumber must not be null or empty.");
        if (proposedPaymentFrequency == null)
            throw new IllegalArgumentException("proposedPaymentFrequency must not be null.");
        if (personsToInsure == null || personsToInsure.isEmpty())
            throw new IllegalArgumentException("personsToInsure must not be null or empty.");
        if (proposedPremium <= 0)
            throw new IllegalArgumentException("proposedPremium must be positive.");

        for (AbstractContract contract : contracts) {
            if (contractNumber.equals(contract.getContractNumber()))
                throw new IllegalArgumentException("contractNumber must be unique.");
        }

        int valueInMonths = proposedPaymentFrequency.getValueInMonths();
        int yearSum = proposedPremium * (12/valueInMonths);

        if(yearSum < personsToInsure.size() * 5)
            throw new IllegalArgumentException("Proposed premium does not meet minimum yearly threshold.");

        ContractPaymentData paymentData = new ContractPaymentData(proposedPremium, proposedPaymentFrequency, currentTime, 0);
        TravelContract newContract = new TravelContract(contractNumber, this, policyHolder, paymentData, personsToInsure.size() * 10, personsToInsure);

        chargePremiumOnContract(newContract);

        contracts.add(newContract);
        policyHolder.addContract(newContract);

        return newContract;
    }

    public MasterVehicleContract createMasterVehicleContract(String contractNumber, Person beneficiary, Person policyHolder){
        for(AbstractContract contract : contracts){
            if (contractNumber.equals(contract.getContractNumber()))
                throw new IllegalArgumentException("contractNumber must be unique.");
        }

        MasterVehicleContract newContract = new MasterVehicleContract(contractNumber, this, beneficiary, policyHolder);

        contracts.add(newContract);
        policyHolder.addContract(newContract);

        return newContract;
    }

    public void moveSingleVehicleContractToMasterVehicleContract(MasterVehicleContract masterVehicleContract, SingleVehicleContract singleVehicleContract){
        if (masterVehicleContract == null || singleVehicleContract == null)
            throw new IllegalArgumentException("masterVehicleContract and singleVehicleContract must not be null.");

        if (!masterVehicleContract.isActive())
            throw new InvalidContractException("masterVehicleContract is not active.");
        if (!singleVehicleContract.isActive())
            throw new InvalidContractException("singleVehicleContract is not active.");
        if (!masterVehicleContract.getPolicyHolder().equals(singleVehicleContract.getPolicyHolder()))
            throw new InvalidContractException("policyHolder mismatch.");
        if (!masterVehicleContract.getInsurer().equals(this) || !singleVehicleContract.getInsurer().equals(this))
            throw new InvalidContractException("contract does not belong to this insurer.");

        masterVehicleContract.getChildContracts().add(singleVehicleContract);

        contracts.remove(singleVehicleContract);
        singleVehicleContract.getPolicyHolder().getContracts().remove(singleVehicleContract);
    }

    public void chargePremiumsOnContracts(){
        for(AbstractContract contract : contracts){
            if(contract.isActive())
                contract.updateBalance();
        }
    }

    public void chargePremiumOnContract(MasterVehicleContract contract){
        for(AbstractContract eachContract : contract.getChildContracts()){
            chargePremiumOnContract(eachContract);
        }
    }

    public void chargePremiumOnContract(AbstractContract contract){
        while(contract.getContractPaymentData().getNextPaymentTime().isBefore(currentTime) || contract.getContractPaymentData().getNextPaymentTime().isEqual(currentTime)) {
            int currentOutstandingBalance = contract.getContractPaymentData().getOutstandingBalance();
            int premium = contract.getContractPaymentData().getPremium();

            contract.getContractPaymentData().setOutstandingBalance(currentOutstandingBalance + premium);
            contract.getContractPaymentData().updateNextPaymentTime();
        }
    }

    public void processClaim(TravelContract travelContract, Set<Person> affectedPersons){
        if (travelContract == null)
            throw new IllegalArgumentException("travelContract must not be null.");
        if (affectedPersons == null || affectedPersons.isEmpty())
            throw new IllegalArgumentException("affectedPersons must not be null or empty.");
        if (!travelContract.getInsuredPersons().containsAll(affectedPersons))
            throw new IllegalArgumentException("affectedPersons must be a subset of insured persons.");
        if (!travelContract.isActive())
            throw new InvalidContractException("travelContract is not active.");

        int payoutAmount = travelContract.getCoverageAmount() / affectedPersons.size();

        for (Person person : affectedPersons) {
            person.payout(payoutAmount);
        }

        travelContract.setInactive();
    }

    public void processClaim(SingleVehicleContract singleVehicleContract, int expectedDamages){
        if (singleVehicleContract == null)
            throw new IllegalArgumentException("singleVehicleContract must not be null.");
        if (expectedDamages <= 0)
            throw new IllegalArgumentException("expectedDamages must be positive.");

        if (!singleVehicleContract.isActive())
            throw new InvalidContractException("singleVehicleContract is not active.");

        Person beneficiary = singleVehicleContract.getBeneficiary();
        int payoutAmount = singleVehicleContract.getCoverageAmount();

        if(beneficiary != null){
            beneficiary.payout(payoutAmount);
        }
        else{
            singleVehicleContract.getPolicyHolder().payout(payoutAmount);
        }

        if(expectedDamages >= 0.7*singleVehicleContract.getInsuredVehicle().getOriginalValue())
            singleVehicleContract.setInactive();
    }
}
