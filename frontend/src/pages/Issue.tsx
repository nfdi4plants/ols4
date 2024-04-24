import { Fragment } from "react";
import { Banner } from "../components/Banner";
import DataTable, { Column } from "../components/DataTable";
import Header from "../components/Header";
import { useEffect } from 'react';


export default function Issue() {
  document.title = "Ontology Lookup Service (OLS)";
  return (
    <Fragment>
      <Header section="Issue" />
      <main className="container mx-auto px-4 my-8">
        <div className="text-2xl font-bold my-6">
          Here you could suggest to add more Ontologies, add new terms or correct descriptions.
        </div>
        <div className="flex justify-center">
        <iframe src="https://docs.google.com/forms/d/e/1FAIpQLSeD1XUJcIRG1fRmT-xCDcAVveGAONryckhTCpyE5Kmhit7X-Q/viewform?embedded=true" width="960" height="843" frameBorder="0" marginHeight="0" marginWidth="0">Loading…</iframe>
         
        </div>
      </main>
    </Fragment>
  );
}



