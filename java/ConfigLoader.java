
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.TreeMap;

import machines.*;

/**
 * Wraps a config file with machine parameters.
 * Used to remember machine settings between sessions.
 */
public class ConfigLoader {

	public final String path;

	public ConfigLoader(String path){
		this.path = path;
	}

	/**
	 * Saves a MachineConfig into the file specified in constructor.
	 * @param config Machine configuration
	 * @param name Name of the machine
	 */
	public void save(MachineConfig config, String name){
		var configData = new TreeMap<String, MachineConfig>();
		configData.put(name, config);

		var builder = new GsonBuilder();
		builder.setPrettyPrinting();
		Gson gson = builder.create();
		String json = gson.toJson(configData);

		try{
			var configWriter = new OutputStreamWriter(new FileOutputStream(path), "UTF-8");
			configWriter.write(json);
			configWriter.close();
		}
		catch(IOException e){
			e.printStackTrace();
		}
	}

	/**
	 * Loads a MachineConfig from the file specified in constructor.
	 * @param name Name of the machine to load
	 */
	public MachineConfig load(String name) throws IOException {
		byte[] bytes = Files.readAllBytes(Paths.get(path));
		String json = new String(bytes, "UTF-8");

		var builder = new GsonBuilder();
		Gson gson = builder.create();
		Type mapType = new TypeToken<TreeMap<String, MachineConfig>>(){}.getType();
		TreeMap<String, MachineConfig> configData;
		try{
			configData = gson.fromJson(json, mapType);
		}
		catch(Exception e){
			return new MachineConfig();
		}

		if(configData.containsKey(name)){
			return configData.get(name);
		}
		else return new MachineConfig();
	}

}
