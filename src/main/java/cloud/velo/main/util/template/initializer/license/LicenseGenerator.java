package cloud.velo.main.util.template.initializer.license;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class LicenseGenerator {

    public String generate(String license, String projectName) {
        String year = String.valueOf(LocalDate.now().getYear());
        return switch (license.toUpperCase()) {
            case "MIT" -> mit(year, projectName);
            case "APACHE-2.0", "APACHE 2.0" -> apache(year, projectName);
            case "GPL-3.0", "GPL 3.0" -> gpl(projectName);
            case "BSD-2-CLAUSE", "BSD 2-CLAUSE" -> bsd2(year, projectName);
            case "BSD-3-CLAUSE", "BSD 3-CLAUSE" -> bsd3(year, projectName);
            case "ISC" -> isc(year, projectName);
            case "UNLICENSED", "NONE" -> "";
            default -> throw new IllegalArgumentException("지원하지 않는 라이선스: " + license);
        };
    }

    private String mit(String year, String projectName) {
        return """
                MIT License
                
                Copyright (c) %s %s
                
                Permission is hereby granted, free of charge, to any person obtaining a copy
                of this software and associated documentation files (the "Software"), to deal
                in the Software without restriction, including without limitation the rights
                to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
                copies of the Software, and to permit persons to whom the Software is
                furnished to do so, subject to the following conditions:
                
                The above copyright notice and this permission notice shall be included in all
                copies or substantial portions of the Software.
                
                THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
                IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
                FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
                AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
                LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
                OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
                SOFTWARE.
                """.formatted(year, projectName);
    }

    private String apache(String year, String projectName) {
        return """
                Apache License
                Version 2.0, January 2004
                
                Copyright (c) %s %s
                
                Licensed under the Apache License, Version 2.0 (the "License");
                you may not use this file except in compliance with the License.
                You may obtain a copy of the License at
                
                    http://www.apache.org/licenses/LICENSE-2.0
                
                Unless required by applicable law or agreed to in writing, software
                distributed under the License is distributed on an "AS IS" BASIS,
                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                See the License for the specific language governing permissions and
                limitations under the License.
                """.formatted(year, projectName);
    }

    private String gpl(String projectName) {
        return """
                GNU GENERAL PUBLIC LICENSE
                Version 3, 29 June 2007
                
                %s
                
                This program is free software: you can redistribute it and/or modify
                it under the terms of the GNU General Public License as published by
                the Free Software Foundation, either version 3 of the License, or
                (at your option) any later version.
                
                This program is distributed in the hope that it will be useful,
                but WITHOUT ANY WARRANTY; without even the implied warranty of
                MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
                GNU General Public License for more details.
                
                You should have received a copy of the GNU General Public License
                along with this program. If not, see <https://www.gnu.org/licenses/>.
                """.formatted(projectName);
    }

    private String bsd2(String year, String projectName) {
        return """
                BSD 2-Clause License
                
                Copyright (c) %s, %s
                
                Redistribution and use in source and binary forms, with or without
                modification, are permitted provided that the following conditions are met:
                
                1. Redistributions of source code must retain the above copyright notice,
                   this list of conditions and the following disclaimer.
                
                2. Redistributions in binary form must reproduce the above copyright notice,
                   this list of conditions and the following disclaimer in the documentation
                   and/or other materials provided with the distribution.
                
                THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
                AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
                IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
                ARE DISCLAIMED.
                """.formatted(year, projectName);
    }

    private String bsd3(String year, String projectName) {
        return """
                BSD 3-Clause License
                
                Copyright (c) %s, %s
                
                Redistribution and use in source and binary forms, with or without
                modification, are permitted provided that the following conditions are met:
                
                1. Redistributions of source code must retain the above copyright notice,
                   this list of conditions and the following disclaimer.
                
                2. Redistributions in binary form must reproduce the above copyright notice,
                   this list of conditions and the following disclaimer in the documentation
                   and/or other materials provided with the distribution.
                
                3. Neither the name of the copyright holder nor the names of its contributors
                   may be used to endorse or promote products derived from this software
                   without specific prior written permission.
                
                THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
                AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
                IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
                ARE DISCLAIMED.
                """.formatted(year, projectName);
    }

    private String isc(String year, String projectName) {
        return """
                ISC License
                
                Copyright (c) %s, %s
                
                Permission to use, copy, modify, and/or distribute this software for any
                purpose with or without fee is hereby granted, provided that the above
                copyright notice and this permission notice appear in all copies.
                
                THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
                WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
                MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
                ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
                WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
                ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
                OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
                """.formatted(year, projectName);
    }
}