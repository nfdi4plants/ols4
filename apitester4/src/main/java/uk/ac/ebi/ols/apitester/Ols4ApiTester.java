
package uk.ac.ebi.ols.apitester;

import java.io.File;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.Map.Entry;

import com.google.gson.*;
import org.apache.commons.io.IOUtils;


public class Ols4ApiTester {


	Gson gson;
	String url, outDir;

	public Ols4ApiTester(String url, String outDir) {

		gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

		if(url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}

		this.url = url;
		this.outDir = outDir;
	}

	public boolean test() throws MalformedURLException, IOException {

		System.out.println("Waiting for API to become available...");

		JsonElement ontologies = null;

		int MAX_RETRIES = 60;

		for(int nRetries = 0; nRetries < MAX_RETRIES; ++ nRetries) {

			ontologies = getAll(url + "/api/ontologies");
			write(outDir + "/ontologies.json", ontologies);

			if(!ontologies.isJsonArray()) {
				try {
					Thread.sleep(1000);
				} catch(InterruptedException e) {}

				continue;
			}
		}

		if(ontologies == null || !ontologies.isJsonArray()) {
			System.out.println("No ontologies returned! :-(");
			return false;
		} else {
			System.out.println("Got " + ontologies.getAsJsonArray().size() + " ontologies");
		}

		JsonElement v2Ontologies = getAll(url + "/api/v2/ontologies");
		write(outDir + "/v2/ontologies.json", v2Ontologies);

		List<String> ontologyIds = new ArrayList();
		for(JsonElement ontology : ontologies.getAsJsonArray()) {
			ontologyIds.add(ontology.getAsJsonObject().get("ontologyId").getAsString());
		}

		for(String ontologyId : ontologyIds) {

			/// v1

			JsonElement classes = getAll(url + "/api/ontologies/" + ontologyId + "/terms");
			write(outDir + "/ontologies/" + ontologyId + "/terms.json", classes);

			JsonElement properties = getAll(url + "/api/ontologies/" + ontologyId + "/properties");
			write(outDir + "/ontologies/" + ontologyId + "/properties.json", properties);

			JsonElement individuals = getAll(url + "/api/ontologies/" + ontologyId + "/individuals");
			write(outDir + "/ontologies/" + ontologyId + "/individuals.json", individuals);


			/// v2

			JsonElement v2Entities = getAll(url + "/api/v2/ontologies/" + ontologyId + "/entities");
			write(outDir + "/v2/ontologies/" + ontologyId + "/entities.json", v2Entities);

			JsonElement v2Classes = getAll(url + "/api/v2/ontologies/" + ontologyId + "/classes");
			write(outDir + "/v2/ontologies/" + ontologyId + "/classes.json", v2Classes);

			JsonElement v2Properties = getAll(url + "/api/v2/ontologies/" + ontologyId + "/properties");
			write(outDir + "/v2/ontologies/" + ontologyId + "/properties.json", v2Properties);

			JsonElement v2Individuals = getAll(url + "/api/v2/ontologies/" + ontologyId + "/individuals");
			write(outDir + "/v2/ontologies/" + ontologyId + "/individuals.json", v2Individuals);

		}

		return true;

	}

	public void write(String path, JsonElement element) throws FileNotFoundException, IOException {

		Files.createDirectories(  Paths.get(path).toAbsolutePath().getParent() );

		File file = new File(path);

		FileOutputStream os = new FileOutputStream(file);

		try {
			os.write( gson.toJson(element).getBytes());
		} finally {
			os.close();
		}
	}

	public JsonElement getAll(String url) {

		try {
			JsonArray allEntries = new JsonArray();

			for(JsonObject res = get(url).getAsJsonObject();;) {

				if(res.has("error")) {
					return res;
				}

				JsonElement embedded = res.get("_embedded");

				if(embedded == null) {
					break;
				}

				String resourceName = embedded.getAsJsonObject().keySet().iterator().next();
				JsonArray entries = embedded.getAsJsonObject().get(resourceName).getAsJsonArray();
				allEntries.addAll(entries);

				JsonObject links = res.get("_links").getAsJsonObject();

				JsonElement nextObj = links.get("next");

				if(nextObj == null) {
					break;
				}

				String next = nextObj.getAsJsonObject().get("href").getAsString();

				res = get(next).getAsJsonObject();
			}

			return deepSort(removeDates(normalizeURLs(allEntries))).getAsJsonArray();

		} catch(Exception e) {
			return gson.toJsonTree(e);
		}
	}

	public JsonElement get(String url) throws IOException {

		System.out.println("GET " + url);

		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

		if (100 <= conn.getResponseCode() && conn.getResponseCode() <= 399) {
			InputStream is = conn.getInputStream();
			Reader reader = new InputStreamReader(is, "UTF-8");
			JsonElement result = JsonParser.parseReader(reader);
			return result;
		} else {
			InputStream is = conn.getErrorStream();
			Reader reader = new InputStreamReader(is, "UTF-8");
			JsonObject error = new JsonObject();
			error.addProperty("error", IOUtils.toString(is, StandardCharsets.UTF_8));
			return error;
		}
	}

	public JsonElement normalizeURLs(JsonElement element) {

		if(element.isJsonArray()) {

			JsonArray arr = element.getAsJsonArray();
			JsonArray res = new JsonArray();
			
			for(int i = 0; i < arr.size(); ++ i) {
				res.add(normalizeURLs(arr.get(i)));
			}

			return res;

		} else if(element.isJsonObject()) {

			JsonObject obj = element.getAsJsonObject();
			JsonObject res = new JsonObject();

			for(Entry<String, JsonElement> entry : obj.entrySet()) {
				res.add(entry.getKey(), normalizeURLs(entry.getValue()));
			}

			return res;

		} else if(element.isJsonPrimitive()) {

			JsonPrimitive p = element.getAsJsonPrimitive();

			if(p.isString()) {

				String replaced = p.getAsString().replace(url, "<base>");
				return new JsonPrimitive(replaced);
			}
		} 

		return element.deepCopy();
	}

	public JsonElement deepSort(JsonElement element) {

		if(element.isJsonArray()) {

			JsonArray arr = element.getAsJsonArray();

			JsonElement[] elems = new JsonElement[arr.size()];

			for(int i = 0; i < arr.size(); ++ i) {
				elems[i] = deepSort(arr.get(i));
			}
			
			Arrays.sort(elems, new Comparator<JsonElement>() {

				public int compare(JsonElement a, JsonElement b) {
					return gson.toJson(a).compareTo(gson.toJson(b));
				}
			});

			JsonArray res = new JsonArray();

			for(int i = 0; i < arr.size(); ++ i) {
				res.add(elems[i]);
			}

			return res;

		} else if(element.isJsonObject()) {

			JsonObject obj = element.getAsJsonObject();

			TreeSet<String> sortedKeys = new TreeSet<String>();

			for(String key : obj.keySet()) {
				sortedKeys.add(key);
			}

			JsonObject res = new JsonObject();

			for(String key : sortedKeys) {
				res.add(key, deepSort(obj.get(key)));
			}

			return res;

		}

		return element.deepCopy();
	}

	public JsonElement removeDates(JsonElement element) {

		if(element.isJsonArray()) {

			JsonArray arr = element.getAsJsonArray();
			JsonArray res = new JsonArray();
			
			for(int i = 0; i < arr.size(); ++ i) {
				res.add(removeDates(arr.get(i)));
			}

			return res;

		} else if(element.isJsonObject()) {

			JsonObject obj = element.getAsJsonObject();
			JsonObject res = new JsonObject();

			for(Entry<String, JsonElement> entry : obj.entrySet()) {

				if(entry.getKey().equals("loaded")) {
					res.add(entry.getKey(), new JsonPrimitive("<loaded>"));
					continue;
				}

				if(entry.getKey().equals("updated")) {
					res.add(entry.getKey(), new JsonPrimitive("<updated>"));
					continue;
				}

				res.add(entry.getKey(), removeDates(entry.getValue()));
			}

			return res;

		}

		return element.deepCopy();
	}

	/*
	public String removeBaseUrl(String url, String baseUrl) {

		if(!url.startsWith(baseUrl)) {
			throw new RuntimeException("url does not start with base url");
		}

		return url.substring(url.length());
	}

	public String doubleEncode(String iri) throws UnsupportedEncodingException {

		return URLEncoder.encode(URLEncoder.encode(iri, "utf-8"), "utf-8");
	}*/

}
