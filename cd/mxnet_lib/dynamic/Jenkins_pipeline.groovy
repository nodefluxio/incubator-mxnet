// -*- mode: groovy -*-

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//
// Jenkins pipeline
// See documents at https://jenkins.io/doc/book/pipeline/jenkinsfile/

// NOTE: ci_utils is loaded by the originating Jenkins job, e.g. jenkins/Jenkinsfile_release_job

// libmxnet location
libmxnet = 'lib/libmxnet.so'

// licenses
licenses = 'licenses/*'

// libmxnet dependencies
mx_deps = ''
mx_mkldnn_deps = 'lib/libdnnl.so.1'

// library type
// either static or dynamic - depending on how it links to its dependencies
libtype = 'dynamic'

libmxnet_pipeline = load('cd/mxnet_lib/mxnet_lib_pipeline.groovy')

// Builds the dynamic binary for the specified mxnet variant
def build(mxnet_variant) {
  node(NODE_LINUX_CPU) {
    ws("workspace/mxnet_${libtype}/${mxnet_variant}/${env.BUILD_NUMBER}") {
      def image = libmxnet_pipeline.get_environment(mxnet_variant)
      ci_utils.init_git()
      ci_utils.docker_run(image, "build_dynamic_libmxnet ${mxnet_variant}", false)
      ci_utils.pack_lib("mxnet_${mxnet_variant}", libmxnet_pipeline.get_stash(mxnet_variant))
    }
  }
}

def get_pipeline(mxnet_variant) {
  return libmxnet_pipeline.get_pipeline(mxnet_variant, this.&build)
}

return this
