# DAILYTASKS

## Day 1

### Coderules

- Every class file name starts with a capital letter
- Entities must end with the `Entity` suffix, e.g. `NameEntity`
- Must use conventional commits for commits
- Must use camelCase notation for code
- Every module must have its own package, e.g. `modules.customer.CustomerEntity`
- Every endpoint must start with `/api`

### Tasks

- Setup Docker
- Create the DB schema
- Create the basic entities → `CustomerEntity`, `AccountEntity`, `TransferEntity`, `AccountTransactionEntity`
- Create the enums for the entities → `RoleEnum`, `StatusEnum`, `TypeEnum`

## Day 2

### Tasks

- Create the controllers
- Create the services
- Create the repositories
- Draft the authentication
- Error handling
- Swagger

## Day 3

### Tasks

- Implement the controllers that are not implemented yet
- Update error handling in the services using the global exception handler
- Create the Spring seed data (not done in Day 2)
- Test all routes on Swagger
- Write the missing tests and review the existing ones
- Add a refresh token or change the type of authentication
- (Optional) Document the Swagger routes so callers know what to pass, with curated examples
