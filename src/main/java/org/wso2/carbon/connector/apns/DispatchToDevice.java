/*
 * Copyright (c) 2005-2013, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * 
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.connector.apns;

import org.apache.synapse.MessageContext;
import org.wso2.carbon.connector.apns.provider.ProviderRegistry;
import org.wso2.carbon.connector.core.AbstractConnector;
import org.wso2.carbon.connector.core.ConnectException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Connector which sends a push notification request to Apple Push Notification
 * service (APNs) for the device token in the connector configuration.
 */
public class DispatchToDevice extends AbstractConnector {

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.wso2.carbon.connector.core.AbstractConnector#connect(org.apache.synapse
     * .MessageContext)
     */
    @Override
    public void connect(MessageContext messageContext) throws ConnectException {
	sendPushNotification(messageContext);
    }

    /**
     * Extracts push notification informations in the message context and send
     * the notification to APNs.
     * 
     * @param messageContext
     *            Synapse message context.
     */
    private void sendPushNotification(MessageContext messageContext) {

	PushNotificationRequest pushNotificationRequest = null;

	try {

	    // Extract the push notification request.
	    pushNotificationRequest = getPushNotificationRequest(messageContext);
	    log.debug(String.format(
		    "<message:%s> Processing APNs request \n%s",
		    messageContext.getMessageID(),
		    pushNotificationRequest.toString()));

	    // Get the notification provider.
	    AbstractPushNotificationProvider pushNotificationProvider = ProviderRegistry
		    .getProvider();
	    log.debug(String.format(
		    "<apns:%s> Push notification provider : <%s>",
		    pushNotificationRequest.getId(), pushNotificationProvider
			    .getClass().getName()));

	    // Send the notification.
	    PushNotificationResponse response = pushNotificationProvider
		    .send(pushNotificationRequest);
	    log.debug(String.format("<apns:%s> Push notification sent.",
		    pushNotificationRequest.getId()));

	    // Build and set a SOAP envelope in the message context.
	    try {
		Utils.setResultEnvelope(messageContext, response);
	    } catch (IOException e) {
		String errorMessage = "Error building the response envelope";
		throw new PushNotificationException(errorMessage,
			Utils.Errors.ERROR_CODE_RESPONSE_BUILDING_FAILURE);
	    }

	} catch (PushNotificationException e) {

	    String errorMessage = String.format(
		    "Error in sending push notification. Error Code : <%s>",
		    e.getErrorCode());
	    Utils.setError(messageContext, e);
	    this.handleException(errorMessage, e, messageContext);
	}

    }

    /**
     * Extracts the push notification request from the message context.
     * 
     * @param messageContext
     *            Synapse message context.
     * @return Extracted push notification request.
     * @throws PushNotificationException
     *             When mandatory parameters are missing.
     */
    private PushNotificationRequest getPushNotificationRequest(
	    MessageContext messageContext) throws PushNotificationException {

	// Get push notification.
	PushNotification pushNotification = getPushNotification(messageContext);

	// Get environment.
	String environment = Utils.getMandatoryPropertyAsString(messageContext,
		Utils.PropertyNames.DESTINATION);

	// Get certificate.
	Certificate certificate = getCertificate(messageContext);

	// Construct and return the push notification request.
	return new PushNotificationRequest(pushNotification, certificate,
		environment);
    }

    /**
     * Extracts certificate info from the message context.
     * 
     * @param messageContext
     *            Synapse message context.
     * @return Extracted certificate.
     * @throws PushNotificationException
     *             When the certificate is missing.
     */
    private Certificate getCertificate(MessageContext messageContext)
	    throws PushNotificationException {

	// Get certificate info.
	String certificateName = Utils
		.getMandatoryPropertyAsString(messageContext,
			Utils.PropertyNames.CERTIFICATE_ATTACHMENT_NAME);
	String password = Utils.getMandatoryPropertyAsString(messageContext,
		Utils.PropertyNames.PASSWORD);

	// Extract certificate content.
	InputStream content = Utils.extractAttachment(messageContext,
		certificateName);

	if (content == null) {
	    String errorMessage = String.format(
		    "Cannot extract certificate attachment for the name %s",
		    certificateName);
	    throw new PushNotificationException(errorMessage,
		    Utils.Errors.ERROR_CODE_INVALID_CERTIFICATE_INFO);
	}

	Certificate certificate = new Certificate(certificateName, content,
		password);

	return certificate;
    }

    /**
     * Extracts the push notification from the message context.
     * 
     * @param messageContext
     *            Synapse messahe context.
     * @return Extracted push notification.
     * @throws PushNotificationException
     *             When device token is missing or empty.
     */
    private PushNotification getPushNotification(MessageContext messageContext)
	    throws PushNotificationException {

	PushNotification pushNotification = new PushNotification();

	// Set payload.
	String alert = Utils.getOptionalPropertyAsString(messageContext,
		Utils.PropertyNames.ALERT);
	pushNotification.setAlert(alert);

	String sound = Utils.getOptionalPropertyAsString(messageContext,
		Utils.PropertyNames.SOUND);
	pushNotification.setSound(sound);

	String badge = Utils.getOptionalPropertyAsString(messageContext,
		Utils.PropertyNames.BADGE);
	pushNotification.setBadge(badge);

	// Set device token.
	String deviceToken = Utils.getMandatoryPropertyAsString(messageContext,
		Utils.PropertyNames.DEVICE_TOKEN);
	if (!deviceToken.trim().isEmpty()) {
	    pushNotification.addDeviceToken(deviceToken);
	} else {
	    throw new PushNotificationException("Device token is empty",
		    Utils.Errors.ERROR_CODE_ILLEGAL_PARAMETER);
	}

	return pushNotification;
    }

}
