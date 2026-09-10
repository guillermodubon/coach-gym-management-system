package io.github.guillermodubon.coachgym.client.application;

/** Replacement definition for the client's optional emergency contact. */
public record UpdateEmergencyContactCommand(
        String fullName,
        String relationship,
        String phone) {

    public UpdateEmergencyContactCommand {
        fullName = ClientCommandValidation.required(
                fullName, "Emergency contact name", 200);
        relationship = ClientCommandValidation.required(
                relationship, "Emergency contact relationship", 100);
        phone = ClientCommandValidation.required(
                phone, "Emergency contact phone", 32);
    }
}
