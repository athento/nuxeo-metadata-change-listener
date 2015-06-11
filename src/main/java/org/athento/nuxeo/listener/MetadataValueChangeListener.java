package org.athento.nuxeo.listener;

import java.security.Principal;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.i18n.I18NUtils;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.comment.api.CommentManager;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;

public class MetadataValueChangeListener implements EventListener {

	public void handleEvent(Event event) throws ClientException {

		if (_log.isDebugEnabled()) {
			_log.debug("Handling event: " + event.getName());
		}
		EventContext ctx = event.getContext();
		if (_log.isDebugEnabled()) {
			_log.debug(" Context is: " + ctx.getClass());
		}
		if ("beforeDocumentModification".equals(event.getName())) {
			try {
				if (!(ctx instanceof DocumentEventContext)) {
					throw new Exception("Context is NOT a DocumentEventContext");
				}
				DocumentEventContext docCtx = (DocumentEventContext)ctx;
				if (_log.isDebugEnabled()) {
					_log.debug(" DocumentContext is: " + docCtx);
				}
				DocumentModel doc = docCtx.getSourceDocument();
				if (_log.isDebugEnabled()) {
					_log.debug(" DocumentModel is: " + doc);
				}
				if (doc == null) {
					throw new Exception("Document in context is null");
				}
				if (_log.isDebugEnabled()) {
					_log.debug(" Getting current version of document: " + doc.getRef());
				}
				CommentManager commentManager = (CommentManager)Framework.getService(CommentManager.class);
				
				CoreSession documentManager = docCtx.getCoreSession();
				DocumentModel currentDocument = documentManager.getDocument(doc.getRef());
				if (_log.isDebugEnabled()) {
					_log.debug(" ... current document is: " + currentDocument);
				}
				if (_log.isDebugEnabled()) {
					_log.debug(" Getting all model schemas: ");
				}
				Locale locale = Locale.getDefault();
				Principal principal = documentManager.getPrincipal();
				try {
					UserManager userManager = (UserManager)Framework.getService(UserManager.class);
					if (_log.isDebugEnabled()) {
						_log.debug(" The principal: " + principal);
					}
					DocumentModel user = userManager.getUserModel(principal.getName());

					String userLocale = String.valueOf(user.getPropertyValue("userprofile:locale"));
					String localeString = userLocale;
					locale = new Locale(localeString);
				}
				catch (Exception e) {
					_log.error("Unable to get locale for user: " + principal, e);
				}

				String bundleName = "messages";

				StringBuilder metadataChanged = new StringBuilder();
				Map<String, Object> metadata;
				Map<String, Object> currentMetadata;
				for (String schema : doc.getSchemas()) {
					if (_log.isDebugEnabled()) {
						_log.debug(" \\_ getting properties for schema: " 
							+ schema);
					}
					metadata = doc.getProperties(schema);
					currentMetadata = currentDocument.getProperties(schema);
					for (String key : metadata.keySet()) {
						Object newValue = metadata.get(key);
						Object oldValue = currentMetadata.get(key);
						if (_log.isDebugEnabled()) {
							_log.debug("  \\_metadata newValue for: " + key 
								+ ": " + newValue);
							_log.debug("      oldValue was: " + oldValue);
						}
						boolean valueChanged = isValueChanged(
							key, oldValue, newValue);
						if (valueChanged) {
							if (_log.isDebugEnabled()) {
								_log.debug("!! VALUE CHANGED for: " + key);
							}
							String msg = I18NUtils.getMessageString(
								bundleName, 
								MetadataValueChangeListener.LOG_MESSAGE_TEMPLATE, 
								new Object[] {
									principal.getName(),
									key,
									toString(oldValue, locale),
									toString(newValue, locale)
								}, locale);
							if (_log.isDebugEnabled()) {
								_log.debug("LOG message: " + msg);
							}
							metadataChanged.append(msg);
							metadataChanged.append(
								MetadataValueChangeListener.LINE_SEPARATOR);
						}
						else if (_log.isDebugEnabled()) {
							_log.debug("   nothing changes");
						}
					}
				}
				if (_log.isDebugEnabled()) {
					_log.debug("Auditing log: " 
						+ MetadataValueChangeListener.LINE_SEPARATOR
						+ metadataChanged.toString());
				}
				if (metadataChanged.length() > 0) {
					if (_log.isDebugEnabled()) {
						_log.debug("Creating comment... ");
					}
					commentManager.createComment(
						doc, metadataChanged.toString(), principal.getName());
				}
			}
			catch (Exception e) {
				_log.error("Unable to handleEvent: " + e.getMessage(), e);
			}
		}
	}

	private boolean isValueChanged(String key, Object oldValue, Object newValue) {
		boolean keyignored = (
			key.contains("dc:contributor")
			||
			key.contains("dc:subject")
		);
		_log.info(" >> key [" + key + "] ignored: " + keyignored);
		boolean result = 
			(
				!(keyignored)
				&&
				(
					( (oldValue == null) && (newValue != null) ) 
					|| 
					( (oldValue != null) && (newValue == null) ) 
					|| 
					(
						( (oldValue != null) && (!oldValue.equals(newValue)) )
						||
						( (newValue != null) && (!newValue.equals(oldValue)) )
					)
				)
			);
//		if (_log.isDebugEnabled()) {
//			_log.debug("??? isValueChanged for key: " + key);
//			_log.debug("    oldValue: " + toString(oldValue, Locale.getDefault()));
//			_log.debug("    newValue: " + toString(newValue, Locale.getDefault()));
//			_log.debug("RESULT: " + result);
//		}
		return result;
	}
	
	private String toString(Object o, Locale locale) {
		StringBuilder sb = new StringBuilder();
		if (o == null) {
			return String.valueOf("NULL");
		}
		if ((o instanceof String)) {
			return (String)o;
		}
		if ((o instanceof String[])) {
			for (String s : (String[])o) {
				sb.append(s);
				sb.append(MetadataValueChangeListener.LINE_SEPARATOR);
			}
		} if ((o instanceof GregorianCalendar)) {
			DateFormat labelDateFormatter = DateFormat.getDateTimeInstance(
				DateFormat.FULL, DateFormat.FULL, locale);
			return labelDateFormatter.format(((GregorianCalendar)o).getTime());
		} if ((o instanceof ArrayList)) {
			Iterator ite = ((ArrayList)o).iterator();
			while (ite.hasNext()) {
				sb.append(ite.next());
				sb.append(MetadataValueChangeListener.LINE_SEPARATOR);
			}
		} else {
			_log.warn("Unable to Stringify object: " 
				+ o.getClass().getCanonicalName());
		}
		return sb.toString();
	}

	private static final String LOG_MESSAGE_TEMPLATE 
		= "label.org.athento.nuxeo.listener.user-X-modified-metadata-Y-from-value-A-to-value-B";
	private static final String LINE_SEPARATOR 
		= System.getProperty("line.separator");
	private static Log _log = LogFactory.getLog(MetadataValueChangeListener.class);
}
