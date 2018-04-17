package com.zby;

import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

import com.zby.service.HelloService;

/**
 * 
 * @author Administrator
 *
 */
@SuppressWarnings("deprecation")
public class XmlBeanFactoryTest {

	public static void main(String[] args) {
		XmlBeanFactory beanFactory = new XmlBeanFactory(new ClassPathResource("applicationContext.xml"));
		HelloService helloService = (HelloService) beanFactory.getBean("helloService");
		System.out.println(helloService.hello("ZBY"));
	}

}
