# learning-datomic

FIXME: description

## Installation

1. See if you have `maven` installed:

    `mvn -v`
    
    If you get useful response, skip to step #4 to install `dev-local`.

2. Install `maven`:

    `brew install maven`
    
    No Homebrew?
    
    `https://brew.sh/`
    
3. Once you think you have `maven` installed, try again:

  `mvn -v`
  
  If it complains about your JAVA_HOME, add this to your `~/.bash_profile`:
  
  `export JAVA_HOME=`/usr/libexec/java_home -v 1.8``
  
  Back in your terminal:
  
  `source ~/.bash_profile`
  
  ...and once more into the breach:
  
  `mvn -v`
  
  No luck? Ping `maverick` on Slack.
  
4. Execute just "Getting Setup" here:

   https://docs.datomic.com/cloud/dev-local.html

## Usage

Each source file/lesson will eventually be just a namespace and bunch of Clojure `(comment...)`s. Evaluate those as you follow the comments.

## License

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
