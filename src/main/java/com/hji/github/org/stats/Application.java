package com.hji.github.org.stats;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;

/**
 * A simple program to aggregate contributor stats for a GitHub org.
 * 
 * Sample GitHub API Urls
 * https://api.github.com/orgs/folio-org/repos?per_page=200
 * https://api.github.com/repos/folio-org/okapi/stats/contributors
 * 
 * Note: use your github account due to API call rate limit
 * 
 * @author hji
 *
 */
@SpringBootApplication
public class Application implements CommandLineRunner {

	public static void main(String[] argsAll) {
		SpringApplication.run(Application.class, argsAll);
	}

	@Override
	public void run(String... args) throws Exception {

		if (args.length < 3) {
			System.out.format(
					"Usage: java -jar github-org-stats.jar <username> <password> <org> [pub|pvt|all], default to public repo only]%n");
			System.exit(1);
		}

		String username = args[0], password = args[1], org = args[2];
		System.out.format("Scan GitHub organization: %s ... %n", org);
		boolean pub = true, pvt = false;
		if (args.length > 3) {
			String scope = args[3].trim().toLowerCase();
			switch (scope) {
			case "pub":
				break;
			case "pvt":
				pvt = true;
				pub = false;
				break;
			case "all":
				pvt = true;
				break;
			default:
				System.out.format("Invalid scope %s. Has to be one of [pub|pvt|all]", args[3]);
				System.exit(1);
			}
		}

		String url = "https://api.github.com";
		String userAgent = "github org stats aggregator";

		Random rand = new Random();
		SortedMap<String, LinkedHashMap<Character, Integer>> rs = new TreeMap<String, LinkedHashMap<Character, Integer>>();
		try {
			// get all repos for the org
			String repoUrl = String.format("%s/orgs/%s/repos?per_page=200", url, org);
			JsonNode reposNode = Unirest.get(repoUrl).header("User-Agent", userAgent).basicAuth(username, password)
					.asJson().getBody();
			if (!reposNode.isArray()) {
				System.out.format("Probably not a GitHub org: %s %n", org);
				System.exit(0);
			}
			JSONArray repos = reposNode.getArray();
			for (int i = 0, n = repos.length(); i < n; i++) {
				JSONObject repo = repos.getJSONObject(i);
				String repoName = repo.getString("name");
				boolean pvtRepo = repo.getBoolean("private");
				if (pvtRepo && !pvt) {
					continue;
				}
				if (!pvtRepo && !pub) {
					continue;
				}
				// get stats for each repo, wait if no data cached by github yet
				System.out.println(repoName);
				Thread.sleep(rand.nextInt(5) * 1000);
				boolean done = false;
				while (!done) {
					String statUrl = String.format("%s/repos/%s/%s/stats/contributors", url, org, repoName);
					JsonNode statsNode = Unirest.get(statUrl).header("User-Agent", userAgent)
							.basicAuth(username, password).asJson().getBody();
					if (!statsNode.isArray()) {
						System.out.format("Strange response %s for repo: %s. Skip it.%n", statsNode, statUrl);
						done = true;
						continue;
					}
					JSONArray stats = statsNode.getArray();
					try {
						for (int j = 0, m = stats.length(); j < m; j++) {
							JSONObject stat = stats.getJSONObject(j);
							String login = stat.getJSONObject("author").getString("login");
							int c = stat.getInt("total");
							int a = 0;
							int d = 0;
							JSONArray weeks = stat.getJSONArray("weeks");
							for (int k = 0; k < weeks.length(); k++) {
								JSONObject week = weeks.getJSONObject(k);
								a += week.getInt("a");
								d += week.getInt("d");
							}
							if (rs.containsKey(login)) {
								LinkedHashMap<Character, Integer> map = rs.get(login);
								map.put('c', c + map.get('c'));
								map.put('a', a + map.get('a'));
								map.put('d', d + map.get('d'));
							} else {
								LinkedHashMap<Character, Integer> map = new LinkedHashMap<Character, Integer>();
								map.put('c', c);
								map.put('a', a);
								map.put('d', d);
								rs.put(login, map);
							}
						}
						done = true;
					} catch (Exception e) {
						System.out.println("Waiting for GitHub API cache ...");
						Thread.sleep(5 * 1000);
					}
				}
			}
		} catch (Throwable th) {
			th.printStackTrace();
		} finally {
			System.out.println("============================================================");
			System.out.println("UserLogin: Commit[Count] Addition[Count] Deletion[Count]");
			System.out.println("============================================================");
			for (Map.Entry<String, LinkedHashMap<Character, Integer>> entry : rs.entrySet()) {
				LinkedHashMap<Character, Integer> map = entry.getValue();
				System.out.format("%s: c[%d] a[%d] d[%d]%n", entry.getKey(), map.get('c'), map.get('a'), map.get('d'));
			}
			System.out.println("============================================================");
		}
	}
}
