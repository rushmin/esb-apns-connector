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

import java.io.IOException;
import java.io.InputStream;

import javax.activation.DataHandler;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axis2.AxisFault;
import org.apache.axis2.transport.TransportUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.core.axis2.Axis2MessageContext;

/**
 * This class possesses a set of utility methods which supports push
 * notification dispatching.
 */
public class Utils {

    private static final Log log = LogFactory.getLog(Utils.class);

    /**
     * Sets the given exception to the message context.
     * 
     * @param messageContext
     *            Synapse message context.
     * @param e
     *            Exception to be set.
     */
    public static void setError(MessageContext messageContext,
	    PushNotificationException e) {

	messageContext.setProperty(SynapseConstants.ERROR_EXCEPTION, e);
	messageContext.setProperty(SynapseConstants.ERROR_MESSAGE,
		e.getMessage());
	messageContext.setProperty(SynapseConstants.ERROR_CODE,
		e.getErrorCode());

	// Remove existing children of body.
	if (messageContext.getEnvelope().getBody().getFirstElement() != null) {
	    messageContext.getEnvelope().getBody().getFirstElement().detach();
	}

	OMFactory factory = OMAbstractFactory.getOMFactory();

	OMNamespace namespace = factory.createOMNamespace(
		SOAPResponseConstants.NS_URI_APNS,
		SOAPResponseConstants.NS_APNS);

	OMElement errorResponse = factory.createOMElement(
		SOAPResponseConstants.TAG_ERROR_RESPONSE, namespace);

	OMElement errorMessage = factory.createOMElement(
		SOAPResponseConstants.TAG_ERROR_MESSAGE, namespace);
	errorMessage.setText(e.getMessage());
	errorResponse.addChild(errorMessage);

	OMElement errorCode = factory.createOMElement(
		SOAPResponseConstants.TAG_ERROR_CODE, namespace);
	errorCode.setText(e.getErrorCode());
	errorResponse.addChild(errorCode);

	messageContext.getEnvelope().getBody().addChild(errorResponse);
    }

    /**
     * Returns the value of the given property key, in the message context.
     * Throws an exception if the property is missing.
     * 
     * @param messageContext
     *            Synapse message context.
     * @param key
     *            Property key.
     * @return Property value
     * @throws PushNotificationException
     *             When the property is missing or not a type of {@link String}
     */
    public static String getMandatoryPropertyAsString(
	    MessageContext messageContext, String key)
	    throws PushNotificationException {

	Object property = messageContext.getProperty(key);

	// Throw an exception if the property is missing.
	if (property == null) {
	    String errorMessage = String.format(
		    "Mandatory property %s is missing.", key);
	    throw new PushNotificationException(errorMessage,
		    Errors.ERROR_CODE_ILLEGAL_PARAMETER);
	}

	// Throw an exception if the property is not a String.
	if (!(property instanceof String)) {
	    String errorMessage = String.format(
		    "Property <%s> is not a String.", key);
	    throw new PushNotificationException(errorMessage,
		    Errors.ERROR_CODE_ILLEGAL_PARAMETER);
	}

	log.debug(String.format("Property found. <key : '%s' , value : '%s'>",
		key, (String) property));

	return (String) property;

    }

    /**
     * Returns the value of the given property key, in the message context,
     * returns null if the property is not found, rather than throwing an
     * exception.
     * 
     * @param messageContext
     *            Synapse message context.
     * @param key
     *            Property key.
     * @return Property value if found, null otherwise.
     * @throws PushNotificationException
     *             When the property is not a type of {@link String}
     */
    public static String getOptionalPropertyAsString(
	    MessageContext messageContext, String key)
	    throws PushNotificationException {

	Object property = messageContext.getProperty(key);

	if (property != null && !(property instanceof String)) {
	    String errorMessage = String.format(
		    "Property <%s> is not a String.", key);
	    throw new PushNotificationException(errorMessage,
		    Errors.ERROR_CODE_ILLEGAL_PARAMETER);
	}

	if (property != null) {
	    log.debug(String.format(
		    "Property found. <key : '%s' , value : '%s'>", key,
		    (String) property));
	} else {
	    log.debug(String.format(
		    "Skipping optional property <%s> not found.", key));
	}

	return (String) property;

    }

    /**
     * Constructs a SOAP envelop using the given push notification response, and
     * sets it in the message context.
     * 
     * @param messageContext
     *            Synapse message context.
     * @param response
     *            Push notification response.
     * @throws AxisFault
     *             When the envelope cannot be created.
     */
    public static void setResultEnvelope(MessageContext messageContext,
	    PushNotificationResponse response) throws AxisFault {

	messageContext.getEnvelope().detach();

	OMFactory factory = OMAbstractFactory.getOMFactory();
	OMNamespace ns = factory.createOMNamespace(
		SOAPResponseConstants.NS_URI_APNS,
		SOAPResponseConstants.NS_APNS);
	OMElement resultTag = factory.createOMElement(
		SOAPResponseConstants.TAG_DISPATCH_TO_DEVICE_RESULT, ns);

	// Add device token tag.
	OMElement resultChild = factory.createOMElement(
		SOAPResponseConstants.TAG_DEVICE_TOKEN, ns);
	resultChild.addChild(factory.createOMText(response.getDeviceToken()));
	resultTag.addChild(resultChild);

	// Add alert tag.
	resultChild = factory.createOMElement(SOAPResponseConstants.TAG_ALERT,
		ns);
	resultChild.addChild(factory.createOMText(response.getAlert()));
	resultTag.addChild(resultChild);

	// Add sound tag.
	resultChild = factory.createOMElement(SOAPResponseConstants.TAG_SOUND,
		ns);
	resultChild.addChild(factory.createOMText(response.getSound()));
	resultTag.addChild(resultChild);

	// Add badge tag.
	resultChild = factory.createOMElement(SOAPResponseConstants.TAG_BADGE,
		ns);
	resultChild.addChild(factory.createOMText(Integer.toString(response
		.getBadge())));
	resultTag.addChild(resultChild);
	
	log.debug("  -- response -- " + resultTag);

	messageContext
		.setEnvelope(TransportUtils.createSOAPEnvelope(resultTag));
    }

    /**
     * Returns the attachment in the message context for the given attachment
     * name.
     * 
     * @param messageContext
     *            Synapse message context.
     * @param attachmentName
     *            Attachment name.
     * @return The content of the attachment as a stream.
     */
    public static InputStream extractAttachment(MessageContext messageContext,
	    String attachmentName) {

	org.apache.axis2.context.MessageContext axis2mc = ((Axis2MessageContext) messageContext)
		.getAxis2MessageContext();

	DataHandler dataHandler = axis2mc.getAttachment(attachmentName);

	if (dataHandler == null) {
	    log.error(String.format("No attachment found for the name %s",
		    attachmentName));
	    return null;
	}

	try {
	    InputStream attachmentInputStream = dataHandler.getInputStream();
	    return attachmentInputStream;
	} catch (IOException e) {
	    log.error(String.format("Cannot read the attachment %s"), e);
	    return null;
	}

    }

    /**
     * Holds error code constants.
     */
    public static final class Errors {

	/**
	 * Parameter, property related errors.
	 */
	public static final String ERROR_CODE_ILLEGAL_PARAMETER = "APNS_000001";

	/**
	 * Certificate processing related errors.
	 */
	public static final String ERROR_CODE_INVALID_CERTIFICATE_INFO = "APNS_000002";

	/**
	 * Wrong push notification service destination.
	 */
	public static final String ERROR_CODE_INVALID_APNS_DESTINATION = "APNS_000003";

	/**
	 * Push notification service errors.
	 */
	public static final String ERROR_CODE_APNS_IO_FAILURE = "APNS_000004";

	/**
	 * SOAP response building errors.
	 */
	public static final String ERROR_CODE_RESPONSE_BUILDING_FAILURE = "APNS_000005";

	/**
	 * Payload validation errors. (e.g. Too long payload)
	 */
	public static final String ERROR_CODE_PAYLOAD_ERROR = "APNS_000006";

    }

    /**
     * Holds String constant of the property keys.
     */
    public static class PropertyNames {

	/**
	 * Key for push notification service destination name.
	 */
	public static final String DESTINATION = "apns.destination";

	/**
	 * Key for certificate attachment name.
	 */
	public static final String CERTIFICATE_ATTACHMENT_NAME = "apns.certificateAttachmentName";

	/**
	 * Key for certificate password.
	 */
	public static final String PASSWORD = "apns.password";

	/**
	 * Key for device token.
	 */
	public static final String DEVICE_TOKEN = "apns.deviceToken";

	/**
	 * Key for alert message.
	 */
	public static final String ALERT = "apns.alert";

	/**
	 * Key for sound clip name.
	 */
	public static final String SOUND = "apns.sound";

	/**
	 * Key for badge value.
	 */
	public static final String BADGE = "apns.badge";

    }

    /**
     * Holds SAOP response constants for tag names, name spaces etc ..
     */
    public static class SOAPResponseConstants {

	/**
	 * Name space URI
	 */
	public static final String NS_URI_APNS = "urn:org.wso2.carbon.connector.apns";
	
	/**
	 * Name space
	 */
	public static final String NS_APNS = "apns";

	
	/**
	 * Name of the result tag.
	 */
	public static final String TAG_DISPATCH_TO_DEVICE_RESULT = "dispatchToDeviceResult";

	
	/**
	 * Name of the device token tag.
	 */
	public static final String TAG_DEVICE_TOKEN = "deviceToken";
	
	/**
	 * Name of the alert message tag.
	 */
	public static final String TAG_ALERT = "alert";
	
	/**
	 * Name of the badge value tag.
	 */
	public static final String TAG_BADGE = "badge";
	
	/**
	 * Name of the sound clip name tag.
	 */
	public static final String TAG_SOUND = "sound";

	
	/**
	 * Name of the error code tag.
	 */
	public static final String TAG_ERROR_CODE = "errorCode";
	
	/**
	 * Name of the error message tag.
	 */
	public static final String TAG_ERROR_MESSAGE = "errorMessage";
	
	/**
	 * Name of the error response tag.
	 */
	public static final String TAG_ERROR_RESPONSE = "errorResponse";
    }
}
