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
	// 标示对象，ConcurrentHashMap不支持null的key和value
	protected static final Object NULL_OBJECT = new Object();
	protected final Log logger = LogFactory.getLog(getClass());
	// 用于缓存单例：name->Object
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<String, Object>(256);
	// 用于缓存单例工厂：name->ObjectFacotry
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<String, ObjectFactory<?>>(16);
	// 用于缓存过早暴露出来的单例：name->earlyObject
	private final Map<String, Object> earlySingletonObjects = new HashMap<String, Object>(16);
	// 已经注册过的单例的name集合
	private final Set<String> registeredSingletons = new LinkedHashSet<String>(256);
	// 正在创建的bean的name集合
	private final Set<String> singletonsCurrentlyInCreation = Collections
			.newSetFromMap(new ConcurrentHashMap<String, Boolean>(16));
	// 创建检查排除
	private final Set<String> inCreationCheckExclusions = Collections
			.newSetFromMap(new ConcurrentHashMap<String, Boolean>(16));
	//
	private Set<Exception> suppressedExceptions;
	// 单例正在销毁中
	private boolean singletonsCurrentlyInDestruction = false;
	// 在工厂销毁时需要调用销毁方法的bean集合
	private final Map<String, Object> disposableBeans = new LinkedHashMap<String, Object>();
	// 缓存【bean的name】和它【包含的bean】的name集合
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<String, Set<String>>(16);
	// 缓存【bean的name】和它所【依赖的bean】的name集合
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<String, Set<String>>(64);
	// 缓存【被依赖的bean】和【依赖它的bean】的name集合
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<String, Set<String>>(64);

	@Override
	public void registerSingleton(String beanName, Object singletonObject) {
		Assert.notNull(beanName, "'beanName' must not be null");
		synchronized (this.singletonObjects) {
			// 查看缓存是否已经存在这个beanName对应的bean
			Object oldObject = this.singletonObjects.get(beanName);
			if (oldObject != null) {
				throw new IllegalStateException("Could not register object [" + singletonObject + "] under bean name '"
						+ beanName + "': there is already object [" + oldObject + "] bound");
			}
			addSingleton(beanName, singletonObject);
		}
	}

	/**
	 * 添加一个完全体单例到容器；需要移除半成体的缓存：对象工厂，早起暴露的引用；
	 * 
	 * @param beanName
	 * @param singletonObject
	 *            可以为null
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
	 *            是否允许过早暴露引用
	 * @return
	 */
	private Object getSingleton(String beanName, boolean allowEarlyReference) {
		Object singletonObject = this.singletonObjects.get(beanName);
		// 完全体缓存中没有，但是已经在创建中了
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			// 这个同步防止addSingleton
			synchronized (this.singletonObjects) {
				singletonObject = this.earlySingletonObjects.get(beanName);
				// 过早暴露的没有，但是可以过早暴露出来，那么就去对象工厂获取，然后暴露出来
				if (singletonObject == null && allowEarlyReference) {
					ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
					if (singletonFactory != null) {
						singletonObject = singletonFactory.getObject();
						// 过早暴露缓存，单例工厂就可以清除了
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
		// 不在创建检查排除列表，并且这个bean正在创建
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	protected void afterSingletonCreation(String beanName) {
		// 不在创建检查排除列表，并且这个bean不是正在创建
		if (!this.inCreationCheckExclusions.contains(beanName)
				&& !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}
}
