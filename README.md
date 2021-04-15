[![License](https://img.shields.io/badge/Apache%202.0-License-blue.svg)](https://raw.githubusercontent.com/bcgov/cpf/gh-pages/LICENSE)
[![Lifecycle](https://img.shields.io/badge/Lifecycle-Stable-97ca00)](https://github.com/bcgov/repomountie/blob/master/doc/lifecycle-badges.md)

# Concurrent Processing Framework (CPF)

The Concurrent Processing Framework (CPF) Client APIs allows web applications to be developed using JavaScript, Java or another programming language to submit batch jobs to the CPF and to download the results of those jobs.

The CPF is a framework for building, deploying and running request/response style web services (business applications).

A business application accepts input parameters, performs some processing using those parameters and then returns the result of the processing.

For example:

* A web map image business application accepts the bounding box, map layers, image size and projection for a map image, it then creates a map image using those parameters and returns the image to the user.
* A geo-coder business application accepts and address and city as parameters, searches the database for the address matches and returns the full address and the point location.

The CPF extends the web service paradigm by adding support for asynchronous processing of a request to a business application and batching of multiple business application requests into a job for asynchronous processing. The asynchronous processing solves the issue of network timeouts/disconnects when waiting for a response for a request. The multiple requests in a single job solves several issues. For the user they only need to submit one request containing all the request parameters rather than thousands or millions of individual requests, this also reduces processing requirements on the clients. For the server/administrator it allows better control over access to the resource as the CPF can distribute the work across multiple worker servers and limit the number of concurrent requests.

The asynchronous processing allows a user to submit the requests for a job with a single web service API call and disconnect from the CPF server. At a later time the user can check the status of the status of the job and if it is completed download the result of all the requests in the job.

## Documentation

See https://bcgov.github.io/cpf/ for full documentation on installing the CPF on a server and
developing custom plug-ins.

## Issues

Issues with the library or documentation can be submitted using the [GitHub Issues List](https://github.com/bcgov/cpf/issues).

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
