# Count directories

```
match(n:DIRECTORY)
WITH 
    count(*) AS nb_dirs
return nb_dirs;
```


# Total number of files

```
match(n:FILE)
WITH 
    count(*) AS nb_files
return nb_files;
```

# The 5 biggets objects

```
match(n)
return n
order by n.length desc
limit 5;
```

# Directories that contains the most objects

```
match (n)<-[r:IS_IN_DIRECTORY]-(m)
return n, count(m)
order by count(m) desc
limit 10;
```


# The 10 most frequent mime-types

```
match (n:FILE)
return n.`mime-type`, count(n.`mime-type`)
order by count(n.`mime-type`) desc
limit 10;
```

# The longest path of nested directories

```
```

# The most living files

ie. the one that were modifed the most recently

```
```

# The most static files

ie. the one that are very rarely modified

```
```