# Performance test

This test verifies the campus activity backend with:

- at least 10,000 rows in the queried `activity` table;
- 100 concurrent virtual users;
- a 30-second ramp-up;
- a five-minute steady run;
- average and P95 response times below 3,000 ms;
- an error rate below 1%.

The workload logs in once per virtual user, then repeatedly requests a random
page of activities and a random activity detail. It uses read-oriented business
flows so duplicate signup/check-in rules do not create artificial errors.

Run the formal test while the backend is available on port 8080:

```powershell
.\performance\run-jmeter.ps1
```

Run a short smoke test:

```powershell
.\performance\run-jmeter.ps1 -Threads 1 -RampUpSeconds 1 -DurationSeconds 10 -RunName smoke
```

Generated result files and the HTML dashboard are written below
`performance/results/`.
