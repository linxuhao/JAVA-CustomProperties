package com.saintgobain.sg4pTool.beans.properties;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;



/**
 * Class to treat property files as a List, the main focus is to deal with property files with order and write in UTF-8 Encoding
 * <br> Also handle multi Thread read/write at same time on same file with a synchronized queue system
 * <br> Also have a little versioning  system
 * <br> conserve duplicate keys(even you are not supposed to have), but can modify only the last occurrence
 * <br> Please save after all modifications, and refresh() to reload from file if you keep working on the same file
 * @author Xuhao 
 */
public class CustomProperties{
	
	/**
	 * 
	 */
	protected static final long serialVersionUID = 1L;
	
	public static final Charset OFFICIAL_WRITE_ENCODING = StandardCharsets.UTF_8;
	
	public static final Charset OFFICIAL_READ_ENCODING = StandardCharsets.ISO_8859_1;
	
	public static final String PROPERTIES_SEPARATOR = "=";
	
	public static final String VERSIONING_TEMP_FOLDER_NAME = "temp";
	
	/**
	 * to handle file writing concurrency
	 */
	private static Map<String,ConcurrentLinkedQueue<Integer>> fileWritingQueueFifo = new HashMap<String,ConcurrentLinkedQueue<Integer>>();
	
	private static Map<String,Boolean> isFileWriting = new HashMap<String,Boolean>();
	
	/**
	 * provide a distinct id for each writing operation in queue
	 */
	private static int writingIdSequance = 0;
	
	/**
	 * the file source
	 */
	private File file;

	/**
	 * !!!!!!!!!!!!!!!!!!!!!!!!!!!! structure reference file, no modification at all on this file!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
	 */
	private File structureReferenceFile;
	
	/**
	 * used when insert/saving the file
	 */
	private List<String> contentList;
	
	/**
	 * The reference file content, only use to compare and to get reference value
	 */
	private List<String> referenceContentList;
	
	/**
	 * only for versioning purpose when saving
	 */
	private StringBuilder oldContent;

	/**
	 * properties map, insert/saving/compare
	 */
	private LinkedHashMap<String,String> properties;
	
	/**
	 * duplicated key , the Integer part of map is it's count
	 */
	private Map<String,Integer> duplicatedKeys;
	
	/**
	 * only used in initialization to load duplicated keys, to not erase duplicated key informations, the number in map indicate values's order, smallest number means the values is the last one
	 */
	private Map<String,Deque<String>> duplicatedValues;
	
	/**
	 * 
	 * @param fileDir : the file to write
	 * @param structureReferenceFileDir : the reference file
	 * @throws IOException
	 * @see isProperties(String) for how to definie if is a properties line or a comment line
	 */
	public CustomProperties(File file, File structureReferenceFile) throws IOException{
		initialize(file,structureReferenceFile);
	}
	
	/**
	 * 
	 * @param fileDir : the file to write
	 * @param structureReferenceFileDir : the reference file
	 * @throws IOException
	 * @see isProperties(String) for how to definie if is a properties line or a comment line
	 */
	public CustomProperties(String fileDir,String structureReferenceFileDir) throws IOException{
		File localFile = new File(fileDir);
		File localRefFile = new File(structureReferenceFileDir);
		initialize(localFile,localRefFile);
	}
	
	/**
	 * refresh this object from file
	 * @throws IOException
	 */
	public void refresh() throws IOException{
		initialize(file,structureReferenceFile);
	}

	private void initialize(File file, File structureReferenceFile) throws IOException{
		this.file = file;
		this.structureReferenceFile = structureReferenceFile;
		//from structureReferenceFile
		contentList = new ArrayList<String>();
		oldContent = new StringBuilder();
		referenceContentList = new ArrayList<String>();
		//from file to write
		properties = new LinkedHashMap<String,String>();
		setDuplicatedKeys(new LinkedHashMap<String,Integer>());
		duplicatedValues = new HashMap<String,Deque<String>>();
		while(isFileWriting.containsKey(file.getAbsolutePath()) && isFileWriting.get(file.getAbsolutePath())){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				throw new IOException("Failed waiting file write finish before raeding", e);
			}
		}
		initializeProperties();
		initializeReferenceContent();
	}

	private void initializeProperties() throws IOException {
		int i = 1;
		String temp = null;
		try(FileInputStream input = new FileInputStream(this.file);
			BufferedReader in = new BufferedReader(new InputStreamReader(input, OFFICIAL_READ_ENCODING));){
			
			//read the entire file with UTF-8 encoding
			while(in.ready()){
				//load the line
				temp = in.readLine();
				loadProperties(StringEscapeUtils.unescapeJava(temp));
				//construct the string
				i++;
			}
			
		}catch(IOException e){
			throw new IOException("[CustomProperties] - Error on line " + i + " : "+ temp +" while initializing writing file", e);
		}
	}
	
	private void initializeReferenceContent() throws IOException {
		int i = 1;
		String temp = null;
		try(FileInputStream refInput = new FileInputStream(this.structureReferenceFile);
			BufferedReader refIn = new BufferedReader(new InputStreamReader(refInput, OFFICIAL_READ_ENCODING))){
			//read the entire file with UTF-8 encoding
			while(refIn.ready()){
				//load the line
				temp = refIn.readLine();
				loadContent(StringEscapeUtils.unescapeJava(temp));
				//construct the string
				i++;
			}
			
		}catch(IOException e){
			throw new IOException("[CustomProperties] - Error on line " + i + " : "+ temp +" while initializing reference file", e);
		}
	}

	/**
	 * load every content into the content list from reference file
	 * @param temp
	 */
	private void loadContent(String temp) {
		String key;
		if(isProperties(temp) && properties.containsKey(key = getKeyFromString(temp))){
			//if is the same row to the last index of duplciate key
			if(duplicatedKeys.containsKey(key)){
				// if there is no duplicated key count left, it is the last duplicated key so
				contentList.add(key + "=" + duplicatedValues.get(key).pollLast());
			}else{
				contentList.add(key + "=" + properties.get(key));
			}		
		}else{
			contentList.add(temp);
		}
		referenceContentList.add(temp);
	}

	/**
	 * check if a line is a properties
	 * @param temp
	 * @return
	 */
	public boolean isProperties(String temp) {
		String localTemp = temp.trim();
		if(localTemp.startsWith("#") || localTemp.startsWith("/-") || localTemp.startsWith(PROPERTIES_SEPARATOR)){
			return Boolean.FALSE;
		}
		return localTemp.contains(PROPERTIES_SEPARATOR);
	}
	
	/**
	 * load properties exit in file to write
	 * @param temp
	 */
	private void loadProperties(String temp) {
		//save the old content for versioning
		oldContent.append(temp).append("\n");
		//construct lists
		String[] splited = temp.split(PROPERTIES_SEPARATOR);
		String key = getKeyFromString(temp);
		String value = getValueFromString(temp);
		if(isProperties(temp)){
			//properties
			if(splited.length >= 1){
				if(properties.containsKey(key)){
					duplicatedKeys.put(key, duplicatedKeys.getOrDefault(key, 1) + 1);
					//if is a duplicated key, save it's value in duplicatedValues
					Deque<String> tempDeque = duplicatedValues.get(key);
					if(tempDeque != null){
						//add one duplicated key's value
						tempDeque.push(value);
					}else{
						tempDeque = new ArrayDeque<String>();
						//save the first duplicated key value
						tempDeque.push(properties.get(key));
						//save the second
						tempDeque.push(value);
						duplicatedValues.put(key, tempDeque);
					}
				}
				properties.put(key, value);
			}else{
				//is a buggy line, just skip it
			}
		}
	}

	public String getValueFromString(String content) {
		String[] splited = content.split(PROPERTIES_SEPARATOR);
		String value = "";
		StringBuilder valueBuilder = new StringBuilder();
		for(int i=1; i<splited.length; i++){
			valueBuilder.append(splited[i]);
		}
		value = valueBuilder.toString();
		return value;
	}

	public String getKeyFromString(String content) {
		String[] splited = content.split(PROPERTIES_SEPARATOR);
		return splited[0];
	}

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}
	
	public Map<String,Integer> getDuplicatedKeys() {
		return duplicatedKeys;
	}

	protected void setDuplicatedKeys(Map<String,Integer> duplicatedKeys) {
		this.duplicatedKeys = duplicatedKeys;
	}

	public LinkedHashMap<String,String> getProperties() {
		return properties;
	}
	
	/**
	 * is suppose to be read only in most case, do not change this unless you know what you are doing
	 * @param structureReferenceFile
	 */
	public void setProperties(LinkedHashMap<String,String> properties) {
		this.properties = properties;
	}
	
	public File getStructureReferenceFile() {
		return structureReferenceFile;
	}
	
	/**
	 * is suppose to be read only in most case, do not change this unless you know what you are doing
	 * @param structureReferenceFile
	 */
	public void setStructureReferenceFile(File structureReferenceFile) {
		this.structureReferenceFile = structureReferenceFile;
	}
	
	/**
	 * 
	 * @return list of keys with empty values, means these are the keys bugged (we are not supposed to have empty keys)
	 */
	public List<PropertiesDifferent> getEmptyKeyList() {
		List<PropertiesDifferent> emptyPropertiesList = new ArrayList<PropertiesDifferent>();
		String oldKey = "";
		for(Entry<String, String> prop : properties.entrySet()){
			if(prop.getValue().isEmpty()){
				PropertiesDifferent empty = new PropertiesDifferent(prop.getKey(),prop.getKey(),"",oldKey);
				emptyPropertiesList.add(empty);
			}
			//save this propertie's key, the next key may need it as the key before this
			oldKey = prop.getKey();
		}
		return emptyPropertiesList;
	}
	
	public List<String> getContentList() {
		return contentList;
	}

	protected void setContentList(List<String> contentList) {
		this.contentList = contentList;
	}
	

	public List<String> getReferenceContentList() {
		return referenceContentList;
	}

	protected void setReferenceContentList(List<String> referenceContentList) {
		this.referenceContentList = referenceContentList;
	}
	

	protected StringBuilder getOldContent() {
		return oldContent;
	}

	protected void setOldContent(StringBuilder oldContent) {
		this.oldContent = oldContent;
	}
	/**
	 * only used in initialization to load duplicated keys, to not erase duplicated key informations, the number in map indicate values's order, smallest number means the values is the last one
	 */
	public Map<String,Deque<String>> getDuplicatedValues() {
		return duplicatedValues;
	}

	protected void setDuplicatedValues(Map<String,Deque<String>> duplicatedValues) {
		this.duplicatedValues = duplicatedValues;
	}
	
	protected List<String> getContentListValue() {
		List<String> contentListValue = new ArrayList<String>();
		for(String content : contentList){
			String value = getValueFromString(content);
			contentListValue.add(value);
		}
		return contentListValue;
	}


	protected List<String> getContentListKey() {
		List<String> contentListKey = new ArrayList<String>();
		for(String content : contentList){
			String key = getKeyFromString(content);
			contentListKey.add(key);
		}
		return contentListKey;
	}
	
	protected List<String> getReferenceContentListKey() {
		List<String> contentListKey = new ArrayList<String>();
		for(String content : referenceContentList){
			String key = getKeyFromString(content);
			contentListKey.add(key);
		}
		return contentListKey;
	}

	
	public boolean containsKey(String key){
		return properties.containsKey(key);
	}
	
	public boolean containsValue(String value){
		return properties.containsValue(value);
	}
	
	/**
	 * 
	 * @param key
	 * @return null if key not exist
	 */
	public String getValueByKey(String key){
		String value = null;
		if(containsKey(key)){
			value = properties.get(key);
		}
		return value;
	}
	
	/**
	 * 
	 * @param key
	 * @return null if key not exist
	 */
	public String getReferenceValueByKey(String key){
		String result = null;
		int index = getReferenceContentListKey().lastIndexOf(key);
		if(index != -1)
		{
			String content = referenceContentList.get(index);
			result = getValueFromString(content);
		}
		return result;
	}
	
	/**
	 * 
	 * @param value
	 * @return a empty list if no key found for this value
	 */
	public List<String> getKeysByReferenceValue(String value){
		List<String> keys = new ArrayList<String>();
		for(String content : referenceContentList){
			String contentValue = getValueFromString(content);
			if(contentValue != null && contentValue.equals(value)){
				keys.add(contentValue);
			}
		}
		return keys;
	}
	
	/**
	 * 
	 * @param value
	 * @return a empty list if no key found for this value
	 */
	public List<String> getKeysByValue(String value){
		List<String> keys = new ArrayList<String>();
		for(Entry<String, String> prop : properties.entrySet()){
			if(prop.getValue()!= null && prop.getValue().equals(value)){
				keys.add(prop.getKey());
			}
		}
		return keys;
	}
	
	/**
	 * find Keys To Translate for other properties file, use this only when you are THE DEFAULT PROPERTIE FILE
	 * @param other
	 * @return
	 */
	public List<PropertiesDifferent> findKeysToTranslate(){
		List<PropertiesDifferent> result = new ArrayList<PropertiesDifferent>();
		String oldKey = "";
		for(int i=0; i<contentList.size(); i++){
			String content = contentList.get(i);
			String key = getKeyFromString(content);
			if(isProperties(content) && !properties.containsKey(key)){
				String referenceValue = getValueFromString(content);
				PropertiesDifferent diffProp = new PropertiesDifferent(key,referenceValue,"",oldKey);
				result.add(diffProp);
			}
			oldKey = key;
		}
		return result;
	}
	
	/**
	 * find bad keys for other properties file, include keys to translate, bad keys are defined in function: 
	 * @param other
	 * @return a list of bad key
	 * @see com.saintgobain.sg4pTool.beans.properties.isBadKey(String key)
	 */
	public List<PropertiesDifferent> findBadKeys(){
		List<PropertiesDifferent> result = new ArrayList<PropertiesDifferent>();
		String oldKey = "";
		for(Entry<String, String> prop : properties.entrySet()){
			String key = prop.getKey();
			if(isBadKey(key)){
				PropertiesDifferent diffProp = new PropertiesDifferent(key,getReferenceValueByKey(key),properties.get(key),oldKey);
				result.add(diffProp);
			}
			oldKey = key;
		}
		return result;
	}
	
	/**
	 * if the key is a bad key xD
	 * @param key
	 * @return
	 */
	public boolean isBadKey(String key) {
		//if empty key
		if(properties.get(key) == null || "".equals(properties.get(key))){
			return Boolean.TRUE;
		}
		return Boolean.FALSE;
	}

	/**
	 * does not insert if value is empty or null
	 * 
	 * @param referenceKey
	 * @param key
	 * @param value
	 * @return true if operation succeed, false if fail
	 * @throws IOException 
	 */
	public boolean insertOrUpdate(String referenceKey, String key, String value) throws IOException{
		if(value != null && !value.isEmpty()){
			//into content list
			List<String> keyList = getContentListKey();
			
			int index = keyList.lastIndexOf(key);
			if(index != -1){
				modifyByKey(index,key,value);
			}else{
				//insert after the current index
				int indexToInsert = keyList.lastIndexOf(referenceKey) + 1;
				insertContentByLineNumber(indexToInsert,key,value);
			}
			return Boolean.TRUE;
		}else{
			return Boolean.FALSE;
		}
	}
	
	private void insertContentByLineNumber(int lineNumber, String key, String value) throws IOException{	
		String content = formContent(key, value);
		contentList.add(lineNumber,content);
		insertOrUpdatePropertiesMap(key, value);	
	}

	private void insertOrUpdatePropertiesMap(String key, String value) {
		if(properties.containsKey(key)){
			properties.replace(key, value);
		}else{
			properties.put(key, value);
		}
	}
	/**
	 * check if is a properties or a comment, and form it
	 * @param key
	 * @param value
	 * @return
	 */
	private String formContent(String key, String value) {
		String content = key;
		if(value != null && !"".equals(value)){
			content = content + "=" + value;
		}
		return content;
	}
	
	/**
	 * modify the first occurrence
	 * @param key
	 * @param newValue
	 * @throws IOException 
	 */
	public void modifyByKey(int indexToModify, String key, String newValue) throws IOException{
		//content list
		String content = formContent(key, newValue);
		contentList.set(indexToModify, content);
		insertOrUpdatePropertiesMap(key, newValue);
	}
	
	/**
	 * This function save new content on currentFile, and save the oldContent in a temp file in temp folder named as filename_timestamp.properties.
	 * only used to versioning local files
	 * @throws IOException
	 */
	public static void saveToFileWithVersioning(File currentFile, String oldContentToSave, String newContentToSave) throws IOException{

		String tempPath = generateTempAbsolutePath(currentFile);
		File tempFile = new File(tempPath);
		File tempParent = tempFile.getParentFile();
		if(!tempParent.exists()){
			tempParent.mkdirs();
		saveToFile(tempFile, oldContentToSave);
		saveToFile(currentFile, newContentToSave);
		}
	}
	
	public static String generateTempAbsolutePath(File currentFile) {
		StringBuilder sb = new StringBuilder();
		Calendar cal = Calendar.getInstance();
		long timestamp = cal.getTimeInMillis();
		String filename = currentFile.getName().split("\\.")[0];

		sb.append(getTempParentFolder(currentFile))
		.append("\\").append(filename).append("_").append(timestamp).append(".properties");
		return sb.toString();
	}

	public static String getTempParentFolder(File currentFile) {
		StringBuilder sb = new StringBuilder();
		String parent = currentFile.getParent();
		sb.append(parent).append("\\").append(VERSIONING_TEMP_FOLDER_NAME);
		return sb.toString();
		
	}

	/**
	 * save the content in file
	 * @throws IOException
	 */
	public static void saveToFile(File filePath, String contentToSave) throws IOException{
		waitForQueue(filePath);
		try(FileOutputStream output = new FileOutputStream(filePath,false);
			BufferedWriter  out = new BufferedWriter(new OutputStreamWriter(output,OFFICIAL_WRITE_ENCODING))){
			isFileWriting.put(filePath.getAbsolutePath(), Boolean.TRUE);
			out.write(contentToSave);
			isFileWriting.put(filePath.getAbsolutePath(), Boolean.FALSE);
		}catch(IOException e){
			throw new IOException("[CustomProperties] - ERROR while saving file to location : " + filePath.getAbsolutePath(), e);
		}finally{
			//remove the thing from the queue when writing finished
			String key = filePath.getAbsolutePath();
			ConcurrentLinkedQueue<Integer> queueForFile = fileWritingQueueFifo.get(key);
			queueForFile.poll();
		}
	}
	
	private static void waitForQueue(File file) throws IOException {
		String key = file.getAbsolutePath();
		int queueId = writingIdSequance++;
		ConcurrentLinkedQueue<Integer> queueForFile;
		
		if(!fileWritingQueueFifo.containsKey(key)){
			queueForFile = new ConcurrentLinkedQueue<Integer>();
			queueForFile.add(queueId);
			fileWritingQueueFifo.put(key, queueForFile);
		}else{
			queueForFile = fileWritingQueueFifo.get(key);
			queueForFile.add(queueId);
		}
		
		while(queueForFile.peek() != null && queueForFile.peek() != queueId){
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				throw new IOException("Failed sleep during queue for file writing",e);
			}
		}
	}

	/**
	 * the old content
	 * @return
	 * @throws IOException
	 */
	public String writeOldContentAsString(){
		return oldContent.toString();
	}
	/**
	 * 
	 * @return
	 * @throws IOException 
	 */
	public String writeContentAsString() throws IOException {
		StringBuilder sb = new StringBuilder();
		for(int i=0; i<contentList.size(); i++){
			String content = contentList.get(i);
			//if is properties
			if(isProperties(content)){
				//write only if it also exist in properties MAP
				String key = getKeyFromString(content);
				if(properties.containsKey(key)){
					sb.append(escapeUnicode(content));
					sb.append("\n");
				}
			}else{
				//write everything else not properties
				sb.append(escapeUnicode(content));
				sb.append("\n");
			}
		}
		return sb.toString();
	}
	
	/**
	 * to generate Chinese properties file automatically, it does not translate to Chinese, it only copy key to value xD
	 * @throws IOException
	 */
	public void iAmChinesePropertiesFile() throws IOException{
		List<String> localReferenceContentList = contentList;
		String oldKey = "";
		for(String content : localReferenceContentList){
			String key = getKeyFromString(content);
			if(isProperties(content)){
				String value = key;
				insertOrUpdate(oldKey, key, value);
			}
			oldKey = key;
		}
	}
	
	/**
	 * it is not a real Chinese file since i can't auto translate English to Chinese xD
	 * <br>it only copy key to value xD
	 * <br> the file will generate in the same directory as referenceFile in this case
	 * 
	 * @param fileNameToGenerate
	 * @param referenceFile
	 * @throws IOException 
	 */
	public static void generateChinesePropertiesFile(String fileNameToGenerate, File referenceFile) throws IOException{
		File fileToGenerate = new File(referenceFile.getParent() + "\\" + fileNameToGenerate);
		generateChinesePropertiesFile(fileToGenerate, referenceFile);
	}
	
	/**
	 * it is not a real Chinese file since i can't auto translate English to Chinese xD
	 * <br>it only copy key to value xD
	 * <br>you have to specify a absolute path in fileToGenerate
	 * @throws IOException 
	 */
	public static void generateChinesePropertiesFile(File fileToGenerate, File referenceFile) throws IOException{
		CustomProperties propToGenerate = new CustomProperties(referenceFile, referenceFile);
		propToGenerate.iAmChinesePropertiesFile();
		CustomProperties.saveToFile(fileToGenerate, propToGenerate.writeContentAsString());
	}
	
	/**
	 * escape special characters with Unicode escape, also escape CR LF to unicode in order to write every content in single line in properties file
	 * @param input
	 * @return
	 * @throws java.io.IOException
	 */
	public static String escapeUnicode(String input){
		StringBuilder b = new StringBuilder();
		
		    for (char c : input.toCharArray()) {
		        if (c >= 128){
		        	 b.append("\\u").append(String.format("%04X", (int) c));
		        }
			    else if(c == '\n' ){
			    	b.append("\\u000a");
			    }else if(c == '\r'){
			    	b.append("\\u000d");
			    }
			    else{
			    	 b.append(c);
			    }
			       
		}
		
		return b.toString();
	}
			 
	 /**
	  * number of properties in this file
	  * @return
	  */
	 public int size(){
		 return this.properties.size();
	 }
	 
	 /**
	  * get properties file local name such as :  en, zh, fr,de
	  * @return
	 * @throws Exception 
	  */
	 public String getLocaleName() throws Exception{
		 return getLocaleName(this.file.getName());
	 }
	 
	 /**
	  * get properties file local name such as :  en, zh, fr,de
	  * @return "" if is the default file
	 * @throws Exception 
	  */
	 public static String getLocaleName(String fileName) throws Exception{
		 String localName = "";
		 try{
			 String[] step1 = fileName.split("\\.");
			 String[] step2 = step1[0].split("_");
			 if(step2.length >= 2){
				 localName = step2[1];
			 }else{
				 //no local name found, assume this is the English(Default) file, so it's localName = \"\", path: " + fileName
			 }
		 }catch(Exception e){
			 throw new Exception("There is a error in file path please check : ",e);
		 }
		 return localName;
	 }
	 
	 /**
	  * Does not ignore case
	  * @param name
	  * @param keysToTranslateOnly
	  * @return
	  */
	 public List<PropertiesDifferent> findAllByNameLike(String name, boolean keysToTranslateOnly){
		 return findAllByNameLikeAndReferenceValueLike(name, keysToTranslateOnly, null);
	 }

	 /**
	  * Does not ignore case on key, but does ignore case on reference value
	  * @param name
	  * @param keysToTranslateOnly
	  * @param value
	  * @return
	  */
	 public List<PropertiesDifferent> findAllByNameLikeAndReferenceValueLike(String name, boolean keysToTranslateOnly, String valueToSearch){
		 List<PropertiesDifferent> result = new ArrayList<PropertiesDifferent>();
			if(keysToTranslateOnly){
				for(PropertiesDifferent  diff : findKeysToTranslate()){
					//if there are value to search, search by key and value, otherwise, search only by key
					if(valueToSearch != null && !"".equals(valueToSearch)){
						if(diff.getKey().contains(name) && StringUtils.containsIgnoreCase(diff.getOriginalValue(), valueToSearch)){
							result.add(diff);
						}
					}else{
						if(diff.getKey().contains(name)){
							result.add(diff);
						}
					}
				}
			}else{
				String oldKey = "";
				for(int i=0; i<contentList.size(); i++){
					String content = contentList.get(i);
					String key = getKeyFromString(content);
					String referenceValue = getReferenceValueByKey(key);
					if(valueToSearch != null && !"".equals(valueToSearch)){
						if(key.contains(name) && isProperties(content) && StringUtils.containsIgnoreCase(referenceValue, valueToSearch)){
							PropertiesDifferent diffProp = new PropertiesDifferent(key,referenceValue,properties.getOrDefault(key, ""),oldKey);
							result.add(diffProp);
						}
					}else{
						if(key.contains(name) && isProperties(content)){
							PropertiesDifferent diffProp = new PropertiesDifferent(key,referenceValue,properties.getOrDefault(key, ""),oldKey);
							result.add(diffProp);
						}
					}
					
					oldKey = key;
				}
			}
			return result;
	 }
	 
	 /**
	  * search with ignore case
	  * @param name
	  * @return
	  */
	 public List<String> findKeysByNameLike(String name){
		 List<String> result = new ArrayList<String>();
			for(int i=0; i<contentList.size(); i++){
				String content = contentList.get(i);
				String key = getKeyFromString(content);
				if(StringUtils.containsIgnoreCase(key, name) && isProperties(content)){
					result.add(key);
				}
			}
			return result;
	 }
	 
	 /**
	  * get this file name
	  */
	 public String getName(){
		 return this.file.getName();
	 }


}
