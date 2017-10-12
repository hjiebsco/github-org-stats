# github-org-stats
Aggregate contributor stats across a GitHub organization

## Build
```
mvn clean package
```

## Run
```
cd target
java -jar github-org-stats.jar <username> <password> <org> [pub|pvt|all]
```
