package com.zby;

import java.text.SimpleDateFormat;

import org.springframework.beans.propertyeditors.CustomDateEditor;

public class CustomDateEditorTest {
	public static void main(String[] args) {
		CustomDateEditor customDateEditor = new CustomDateEditor(new SimpleDateFormat("yyyyMMdd HH:mm:ss"), true, 17);
		customDateEditor.setAsText("20180409 22:22:22");
		System.out.println(customDateEditor.getAsText());
		System.out.println(customDateEditor.getValue());
	}
}
