package com.triphuddle.amqp.user;

import java.util.List;

/** Every queue the user contracts define, declared on the broker by {@code RabbitMQConfig}. */
public class UserQueues {
    public static final List<String> QUEUE_NAMES = List.of(
            UserRegisteredContract.USER_REGISTERED_QUEUE_NAME,
            UserRegisteredContract.USER_REGISTERED_DLQ_NAME,
            UserProfilePictureUpdatedContract.USER_PROFILE_PICTURE_UPDATED_QUEUE_NAME,
            UserProfilePictureUpdatedContract.USER_PROFILE_PICTURE_UPDATED_DLQ_NAME,
            UserEmailVerifiedContract.USER_EMAIL_VERIFIED_QUEUE_NAME,
            UserEmailVerifiedContract.USER_EMAIL_VERIFIED_DLQ_NAME,
            UserMatchedToGroupInvitationContract.USER_MATCHED_TO_GROUP_INVITATION_QUEUE_NAME,
            UserMatchedToGroupInvitationContract.USER_MATCHED_TO_GROUP_INVITATION_DLQ_NAME,
            UserDisabledContract.USER_DISABLED_QUEUE_NAME,
            UserDisabledContract.USER_DISABLED_DLQ_NAME,
            UserDisabledContract.USER_DISABLED_PAYMENT_QUEUE_NAME,
            UserDisabledContract.USER_DISABLED_PAYMENT_DLQ_NAME,
            UserDeletedContract.USER_DELETED_QUEUE_NAME,
            UserDeletedContract.USER_DELETED_DLQ_NAME,
            UserDeletedContract.USER_DELETED_PAYMENT_QUEUE_NAME,
            UserDeletedContract.USER_DELETED_PAYMENT_DLQ_NAME,
            UserDeletedContract.USER_DELETED_DESTINATION_QUEUE_NAME,
            UserDeletedContract.USER_DELETED_DESTINATION_DLQ_NAME,
            UserDeviceRegisteredContract.USER_DEVICE_REGISTERED_QUEUE_NAME,
            UserDeviceRegisteredContract.USER_DEVICE_REGISTERED_DLQ_NAME,
            UserDeviceUnregisteredContract.USER_DEVICE_UNREGISTERED_QUEUE_NAME,
            UserDeviceUnregisteredContract.USER_DEVICE_UNREGISTERED_DLQ_NAME,
            UserContactUpdatedContract.USER_CONTACT_UPDATED_QUEUE_NAME,
            UserContactUpdatedContract.USER_CONTACT_UPDATED_DLQ_NAME,
            UserTravelDocumentsUpdatedContract.USER_TRAVEL_DOCUMENTS_UPDATED_QUEUE_NAME,
            UserTravelDocumentsUpdatedContract.USER_TRAVEL_DOCUMENTS_UPDATED_DLQ_NAME
    );
}
