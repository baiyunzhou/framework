package com.zby.spring;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {
	// ��ʾ����ConcurrentHashMap��֧��null��key��value
	protected static final Object NULL_OBJECT = new Object();
	protected final Log logger = LogFactory.getLog(getClass());
	// ���ڻ��浥����name->Object
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<String, Object>(256);
	// ���ڻ��浥��������name->ObjectFacotry
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<String, ObjectFactory<?>>(16);
	// ���ڻ�����籩¶�����ĵ�����name->earlyObject
	private final Map<String, Object> earlySingletonObjects = new HashMap<String, Object>(16);
	// �Ѿ�ע����ĵ�����name����
	private final Set<String> registeredSingletons = new LinkedHashSet<String>(256);
	// ���ڴ�����bean��name����
	private final Set<String> singletonsCurrentlyInCreation = Collections
			.newSetFromMap(new ConcurrentHashMap<String, Boolean>(16));
	// ��������ų�
	private final Set<String> inCreationCheckExclusions = Collections
			.newSetFromMap(new ConcurrentHashMap<String, Boolean>(16));
	//
	private Set<Exception> suppressedExceptions;
	// ��������������
	private boolean singletonsCurrentlyInDestruction = false;
	// �ڹ�������ʱ��Ҫ�������ٷ�����bean����
	private final Map<String, Object> disposableBeans = new LinkedHashMap<String, Object>();
	// ���桾bean��name��������������bean����name����
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<String, Set<String>>(16);
	// ���桾bean��name����������������bean����name����
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<String, Set<String>>(64);
	// ���桾��������bean���͡���������bean����name����
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<String, Set<String>>(64);

	@Override
	public void registerSingleton(String beanName, Object singletonObject) {
		Assert.notNull(beanName, "'beanName' must not be null");
		synchronized (this.singletonObjects) {
			// �鿴�����Ƿ��Ѿ��������beanName��Ӧ��bean
			Object oldObject = this.singletonObjects.get(beanName);
			if (oldObject != null) {
				throw new IllegalStateException("Could not register object [" + singletonObject + "] under bean name '"
						+ beanName + "': there is already object [" + oldObject + "] bound");
			}
			addSingleton(beanName, singletonObject);
		}
	}

	/**
	 * ���һ����ȫ�嵥������������Ҫ�Ƴ������Ļ��棺���󹤳�������¶�����ã�
	 * 
	 * @param beanName
	 * @param singletonObject
	 *            ����Ϊnull
	 */
	private void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.put(beanName, (singletonObject != null ? singletonObject : NULL_OBJECT));
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.add(beanName);
		}
	}

	@Override
	public Object getSingleton(String beanName) {
		return getSingleton(beanName, true);
	}

	/**
	 * 
	 * @param beanName
	 * @param allowEarlyReference
	 *            �Ƿ�������籩¶����
	 * @return
	 */
	private Object getSingleton(String beanName, boolean allowEarlyReference) {
		Object singletonObject = this.singletonObjects.get(beanName);
		// ��ȫ�建����û�У������Ѿ��ڴ�������
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			// ���ͬ����ֹaddSingleton
			synchronized (this.singletonObjects) {
				singletonObject = this.earlySingletonObjects.get(beanName);
				// ���籩¶��û�У����ǿ��Թ��籩¶��������ô��ȥ���󹤳���ȡ��Ȼ��¶����
				if (singletonObject == null && allowEarlyReference) {
					ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
					if (singletonFactory != null) {
						singletonObject = singletonFactory.getObject();
						// ���籩¶���棬���������Ϳ��������
						this.earlySingletonObjects.put(beanName, singletonObject);
						this.singletonFactories.remove(beanName);
					}
				}
			}
		}
		return (singletonObject != NULL_OBJECT ? singletonObject : null);
	}

	private boolean isSingletonCurrentlyInCreation(String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	@Override
	public String[] getSingletonNames() {
		synchronized (this.singletonObjects) {
			return StringUtils.toStringArray(this.registeredSingletons);
		}
	}

	@Override
	public int getSingletonCount() {
		synchronized (this.singletonObjects) {
			return this.registeredSingletons.size();
		}
	}

	@Override
	public Object getSingletonMutex() {
		return this.singletonObjects;
	}

	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "'beanName' must not be null");
		synchronized (this.singletonObjects) {
			Object singletonObject = this.singletonObjects.get(beanName);
			if (singletonObject == null) {
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction "
									+ "(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
				beforeSingletonCreation(beanName);
				boolean newSingleton = false;
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					this.suppressedExceptions = new LinkedHashSet<Exception>();
				}
				try {
					singletonObject = singletonFactory.getObject();
					newSingleton = true;
				} catch (IllegalStateException ex) {
					// Has the singleton object implicitly appeared in the
					// meantime ->
					// if yes, proceed with it since the exception indicates
					// that state.
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						throw ex;
					}
				} catch (BeanCreationException ex) {
					if (recordSuppressedExceptions) {
						for (Exception suppressedException : this.suppressedExceptions) {
							ex.addRelatedCause(suppressedException);
						}
					}
					throw ex;
				} finally {
					if (recordSuppressedExceptions) {
						this.suppressedExceptions = null;
					}
					afterSingletonCreation(beanName);
				}
				if (newSingleton) {
					addSingleton(beanName, singletonObject);
				}
			}
			return (singletonObject != NULL_OBJECT ? singletonObject : null);
		}
	}

	protected void beforeSingletonCreation(String beanName) {
		// ���ڴ�������ų��б��������bean���ڴ���
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	protected void afterSingletonCreation(String beanName) {
		// ���ڴ�������ų��б��������bean�������ڴ���
		if (!this.inCreationCheckExclusions.contains(beanName)
				&& !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}
}
