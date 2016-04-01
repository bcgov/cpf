# Concurrent Processing Framework (CPF)

This branch contains the documentation for the Concurrent Processing Framework (CPF). See
https://bcgov.github.io/cpf/ to view the documentation.

**DO NOT MODIFY THIS BRANCH DIRECTLY IT IS CREATED USING THE MAVEN SITE PLUGIN**

## Deploying Site

The site can only be deployed be authorized users. Before deploying for the first time add
the following server to the `.m2/settings.xml` file in your home directory.

```xml
<settings>
  <servers>
    <server>
      <id>cpfgh-pages</id>
      <username>[Your Github Username]</username>
      <password>[Your Github Password]</password>
    </server>
   </servers>
</settings>
```

To deploy the site run the following command. This will update the gh-pages branch and automatically
deploy the site to https://bcgov.github.io/cpf/.

```
mvn site:site site:deploy
```

## License

    Copyright © 2008-2016 Province of British Columbia

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at 

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
