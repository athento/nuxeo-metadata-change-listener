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
import org.nuxeo.ecm.automation.features.PlatformFunctions;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.comment.api.CommentManager;
import org.nuxeo.ecm.platform.ui.web.util.files.FileUtils.TemporaryFileBlob;
import org.nuxeo.ecm.user.center.profile.localeProvider.UserLocaleProvider;
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
		try {
			if ("beforeDocumentModification".equals(event.getName())) {
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
				boolean documentTypeMustBeChecked = isDocumentTraceable(doc);
				if (documentTypeMustBeChecked) {
					if (_log.isDebugEnabled()) {
						_log.debug(" Getting current version of document: " 
							+ doc.getRef());
					}
					CommentManager commentManager = (CommentManager)Framework
						.getService(CommentManager.class);
					CoreSession documentManager = docCtx.getCoreSession();
					Principal principal = documentManager.getPrincipal();
					DocumentModel currentDocument = documentManager.getDocument(
						doc.getRef());
					if (_log.isDebugEnabled()) {
						_log.debug(" ... current document is: " + currentDocument);
						_log.debug(" Getting all model schemas: ");
					}
					Locale locale = getLocaleForUser(documentManager);

					StringBuilder metadataChanged = new StringBuilder();
					Map<String, Object> metadata;
					Map<String, Object> currentMetadata;
					for (String schemaName : doc.getSchemas()) {
						if (_log.isDebugEnabled()) {
							_log.debug(" \\_ getting properties for schema: " 
								+ schemaName);
						}
						metadata = doc.getProperties(schemaName);
						currentMetadata = currentDocument.getProperties(schemaName);
						for (String keyName : metadata.keySet()) {
							if (_log.isDebugEnabled()) {
								_log.debug("  + analyzing changes for: " 
									+ keyName); 
							}
							String key = cleanKeyName(keyName);
							if (ignoreKey(keyName)) {
								if (_log.isDebugEnabled()) {
									_log.debug("** value change for [" + keyName 
										+ "] ignored!");
								}
							} else {
								Object newValue = metadata.get(keyName);
								Object oldValue = currentMetadata.get(keyName);
								if (_log.isDebugEnabled()) {
									_log.debug("   - newValue  is: " + newValue);
									_log.debug("   - oldValue was: " + oldValue);
								}
								boolean valueChanged = isValueChanged(
									keyName, oldValue, newValue);
								if (valueChanged) {
									if (_log.isDebugEnabled()) {
										_log.debug("=> VALUE CHANGED for: " 
											+ keyName);
									}
									String keyVocabularyName = Framework
										.getProperty(getPropertyNameForMetadata(
											schemaName, key));
									String translatedOldValue = translateValue(
										oldValue, keyVocabularyName, locale);
									String translatedNewValue = translateValue(
										newValue, keyVocabularyName, locale);
									String labelToTranslate = "label." 
										+ schemaName + "." + key;
									String translatedKey = I18NUtils.getMessageString(
										MetadataValueChangeListener.BUNDLE_NAME,
										labelToTranslate, 
										MetadataValueChangeListener.EMPTY_ARRAY, 
										locale);
									if (_log.isDebugEnabled()) {
										_log.debug(" labels to translate: "); 
										_log.debug("  -> keyVocabularyName: " 
											+ keyVocabularyName);
										_log.debug("  -> translatedKey: " 
											+ translatedKey);
										_log.debug("  -> translatedOldValue: " 
											+ translatedOldValue);
										_log.debug("  -> translatedNewValue: " 
											+ translatedNewValue);
									}
									String msg = I18NUtils.getMessageString(
										MetadataValueChangeListener.BUNDLE_NAME, 
										MetadataValueChangeListener.LOG_MESSAGE_TEMPLATE, 
										new Object[] {
											principal.getName(),
											translatedKey,
											translatedOldValue,
											translatedNewValue
										}, locale);
									if (_log.isDebugEnabled()) {
										_log.debug("Comment message: " + msg);
									}
									metadataChanged.append(msg);
									metadataChanged.append(
										MetadataValueChangeListener.LINE_SEPARATOR);
								}
								else if (_log.isDebugEnabled()) {
									_log.debug("=== nothing changes");
								}
							}
						}
					}
					if (metadataChanged.length() > 0) {
						if (_log.isDebugEnabled()) {
							_log.debug("Creating comment... " 
								+ MetadataValueChangeListener.LINE_SEPARATOR
								+ metadataChanged.toString());
						}
						commentManager.createComment(
							doc, metadataChanged.toString(), principal.getName());
					} else {
						_log.info("No changes found. No comment will be created");
					}
				} else {
					if (_log.isDebugEnabled()) {
						_log.debug("! Document type: " + doc.getDocumentType().getName() 
							+ " not traced. To trace it, include it in this key in properties file: " 
							+ MetadataValueChangeListener.PROPERTY_TRACED_DOCUMENT_TYPES);
					}
				}
			}
		}
		catch (Exception e) {
			_log.error("Unable to handleEvent: " + e.getMessage(), e);
		}
	}

	private String cleanKeyName(String keyName) {
		String[] keyParts = keyName.split(":");
		String key = keyParts[0];
		if (keyParts.length > 0) {
			key = keyParts[keyParts.length-1];
		}
		return key;
	}

	private Locale getLocaleForUser(CoreSession session) {
		Locale locale = Locale.getDefault();
		try {
			if (_log.isDebugEnabled()) {
				_log.debug(" The session: " + session);
			}
			UserLocaleProvider lp = new UserLocaleProvider();
			locale = lp.getLocale(session);
			if (_log.isDebugEnabled()) {
				_log.debug(" Locale for principal is: " + locale);
			}
		}
		catch (Exception e) {
			_log.error("Unable to get locale for coreSession: " + session, e);
			locale = Locale.getDefault();
			_log.warn("Getting default locale: " + locale);
		}
		return locale;
	}

	private String getPropertyNameForMetadata(String schemaName, String keyName) {
		String key = "vocabulary." + schemaName + "." + keyName;
		return key;
	}

	private boolean ignoreKey(String key) {
		boolean keyignored = true;
		String prefixesTraced = Framework.getProperty(
			MetadataValueChangeListener.PROPERTY_TRACED_METADATA_PREFIXES);
		if (_log.isDebugEnabled()) {
			_log.debug("     (property " 
				+ MetadataValueChangeListener.PROPERTY_TRACED_METADATA_PREFIXES 
				+ " value: " + prefixesTraced + ")");
		}
	
		String[] prefixes = prefixesTraced.split(",");
		for (String prefix: prefixes) {
			if (key.startsWith(prefix)) {
				if (_log.isDebugEnabled()) {
					_log.debug("     this metadata must be checked!");
				}
				return false;
			}
		}
		if (_log.isDebugEnabled()) {
			_log.debug("     this metadata must NOT be checked");
		}
		return keyignored;
	}

	private boolean isDocumentTraceable(DocumentModel doc) {
		String documentTypeName = doc.getDocumentType().getName();
		String documentTypesTraced = Framework.getProperty(
			MetadataValueChangeListener.PROPERTY_TRACED_DOCUMENT_TYPES);
		if (_log.isDebugEnabled()) {
			_log.debug("     (property " 
				+ MetadataValueChangeListener.PROPERTY_TRACED_DOCUMENT_TYPES 
				+ " value: " + documentTypesTraced + ")");
		}
		String[] types = documentTypesTraced.split(",");
		boolean traceDocument = false;
		for (String type: types) {
			if (documentTypeName.equals(type)) {
				if (_log.isDebugEnabled()) {
					_log.debug("     this documentType must be checked!");
				}
				traceDocument = true;
				break;
			}
		}
		return traceDocument;
	}

	private boolean isValueChanged(String key, Object oldValue, Object newValue) {
			boolean result = 
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
			return String.valueOf(" ");
		}
		if ((o instanceof String)) {
			return (String)o;
		}
		if ((o instanceof java.lang.Boolean)) {
			return I18NUtils.getMessageString(
				MetadataValueChangeListener.BUNDLE_NAME, ((Boolean)o).toString(),
				null, locale);
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
		} if ((o instanceof TemporaryFileBlob)) {
			return ((TemporaryFileBlob)o).getFilename();
		} else {
			_log.warn("Unable to Stringify object: " 
				+ o.getClass().getCanonicalName());
		}
		return sb.toString();
	}

	private String translateValue(Object value, String keyVocabularyName,
		Locale locale) throws Exception {
		if (keyVocabularyName == null || keyVocabularyName.isEmpty()) {
			return toString(value, locale);
		}
		if (value == null) {
			return "";
		}
		if (pf != null) {
			String valueLabel = pf.getVocabularyLabel(
				keyVocabularyName, toString(value, locale));
			String translatedValue = I18NUtils.getMessageString(
				MetadataValueChangeListener.BUNDLE_NAME, valueLabel, 
				MetadataValueChangeListener.EMPTY_ARRAY, 
				locale);
			return translatedValue;
		} else {
			return toString(value,locale);
		}
	}

	private static final String BUNDLE_NAME = "messages";
	private static final PlatformFunctions pf = new PlatformFunctions();
	private static final String[] EMPTY_ARRAY =  new String[0];
	private static final String LINE_SEPARATOR 
		= System.getProperty("line.separator");
	private static final String LOG_MESSAGE_TEMPLATE 
		= "label.org.athento.nuxeo.listener.user-X-modified-metadata-Y-from-value-A-to-value-B";
	private static final String PROPERTY_TRACED_METADATA_PREFIXES 
		= "traced.metadata.prefixes";
	private static final String PROPERTY_TRACED_DOCUMENT_TYPES 
		= "traced.document.types";
	private static Log _log = LogFactory.getLog(
		MetadataValueChangeListener.class);
}
