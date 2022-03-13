import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormControl, FormGroup } from '@angular/forms';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {

  baseUrl = "http://localhost:8090/"
  //baseUrl = "https://422d-77-21-209-68.ngrok.io/"
  form = new FormGroup({
    direction: new FormControl(''),
    name: new FormControl('')
  });
  processId: String = ""

  constructor(private http: HttpClient) { }

  ngOnInit(): void {
  }

  async orderCoffee() {
    console.log("form values", this.form.value)
    this.http.post<any>(this.baseUrl + 'startInstance', {
      kafkaTopicName: "coffee",
      kafkaKey: "1",
      direction: this.form.value.direction,
      name: this.form.value.name
    }).subscribe(data => {
      console.log("data", data);
      this.processId = data
    })
  }
}
